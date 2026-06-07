package io.github.usmanovmahmudkhan.realtimechat.core.protocol;

import io.github.usmanovmahmudkhan.realtimechat.core.domain.DurableMessage;
import io.github.usmanovmahmudkhan.realtimechat.core.util.UuidV7;

import java.time.Instant;
import java.util.UUID;

public record ServerEvent(
        int protocolVersion,
        UUID eventId,
        ServerEventType type,
        UUID tenantId,
        UUID roomId,
        Instant timestamp,
        String correlationId,
        ErrorCode errorCode,
        Long retryAfterMillis,
        DurableMessage message,
        String detail
) {
    public static ServerEvent chat(DurableMessage message, String correlationId) {
        return new ServerEvent(2, message.messageId(), ServerEventType.CHAT, message.tenantId(),
                message.roomId(), message.createdAt(), correlationId, null, null, message, null);
    }

    public static ServerEvent error(UUID tenantId, UUID roomId, String correlationId,
                                    ErrorCode code, String detail) {
        return new ServerEvent(2, UuidV7.next(), ServerEventType.ERROR, tenantId, roomId,
                Instant.now(), correlationId, code, null, null, detail);
    }
}
