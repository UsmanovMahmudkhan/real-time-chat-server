package io.github.usmanovmahmudkhan.realtimechat.core.service;

import io.github.usmanovmahmudkhan.realtimechat.core.domain.AuthenticatedUser;
import io.github.usmanovmahmudkhan.realtimechat.core.domain.DurableMessage;
import io.github.usmanovmahmudkhan.realtimechat.core.domain.MessageStatus;
import io.github.usmanovmahmudkhan.realtimechat.core.domain.RoomAccess;
import io.github.usmanovmahmudkhan.realtimechat.core.port.AccessRepository;
import io.github.usmanovmahmudkhan.realtimechat.core.port.CoordinationService;
import io.github.usmanovmahmudkhan.realtimechat.core.port.MessageRepository;
import io.github.usmanovmahmudkhan.realtimechat.core.protocol.ErrorCode;
import io.github.usmanovmahmudkhan.realtimechat.core.protocol.ProtocolException;
import io.github.usmanovmahmudkhan.realtimechat.core.protocol.ServerEvent;
import io.github.usmanovmahmudkhan.realtimechat.core.util.UuidV7;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MessageCommandService {
    private final MessageRepository messages;
    private final AccessRepository accessRepository;
    private final CoordinationService coordination;
    private final AuthorizationService authorization;
    private final int maximumMessageLength;
    private final int historyPageSize;

    public MessageCommandService(MessageRepository messages, AccessRepository accessRepository,
                                 CoordinationService coordination, AuthorizationService authorization,
                                 int maximumMessageLength, int historyPageSize) {
        this.messages = messages;
        this.accessRepository = accessRepository;
        this.coordination = coordination;
        this.authorization = authorization;
        this.maximumMessageLength = maximumMessageLength;
        this.historyPageSize = historyPageSize;
    }

    public DurableMessage send(AuthenticatedUser user, UUID tenantId, UUID roomId,
                               UUID idempotencyKey, String content, String correlationId) {
        requireTenant(user, tenantId);
        if (idempotencyKey == null || content == null || content.isBlank()
                || content.length() > maximumMessageLength) {
            throw new ProtocolException(ErrorCode.INVALID_MESSAGE, "Invalid chat message");
        }
        RoomAccess access = roomAccess(user, tenantId, roomId);
        if (!authorization.canSend(user, access)) {
            throw new ProtocolException(ErrorCode.UNAUTHORIZED, "Room send permission denied");
        }
        if (!coordination.allow("user-message", user.userId().toString(), 20, Duration.ofSeconds(1))
                || !coordination.allow("tenant-message", tenantId.toString(), 1000, Duration.ofSeconds(1))) {
            throw new ProtocolException(ErrorCode.RATE_LIMITED, "Message rate limit exceeded");
        }
        var duplicate = messages.findByIdempotencyKey(tenantId, user.userId(), idempotencyKey);
        if (duplicate.isPresent()) {
            return duplicate.get();
        }
        DurableMessage message = new DurableMessage(UuidV7.next(), tenantId, roomId, user.userId(),
                user.displayName(), content, MessageStatus.ACTIVE, Instant.now());
        DurableMessage persisted = messages.persist(message, idempotencyKey, correlationId);
        return persisted;
    }

    public boolean canJoin(AuthenticatedUser user, UUID tenantId, UUID roomId) {
        requireTenant(user, tenantId);
        return authorization.canJoin(user, roomAccess(user, tenantId, roomId));
    }

    public List<DurableMessage> resume(AuthenticatedUser user, UUID tenantId, UUID roomId, UUID afterMessageId) {
        requireTenant(user, tenantId);
        if (!authorization.canJoin(user, roomAccess(user, tenantId, roomId))) {
            throw new ProtocolException(ErrorCode.UNAUTHORIZED, "Room read permission denied");
        }
        return messages.findAfter(tenantId, roomId, afterMessageId, historyPageSize);
    }

    private RoomAccess roomAccess(AuthenticatedUser user, UUID tenantId, UUID roomId) {
        return accessRepository.findRoomAccess(user, tenantId, roomId)
                .orElseThrow(() -> new ProtocolException(ErrorCode.UNAUTHORIZED, "Room access denied"));
    }

    private void requireTenant(AuthenticatedUser user, UUID tenantId) {
        if (!user.tenantId().equals(tenantId)) {
            throw new ProtocolException(ErrorCode.TENANT_MISMATCH, "Tenant mismatch");
        }
    }
}
