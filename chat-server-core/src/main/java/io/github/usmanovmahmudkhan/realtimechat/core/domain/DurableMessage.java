package io.github.usmanovmahmudkhan.realtimechat.core.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DurableMessage(
        UUID messageId,
        UUID tenantId,
        UUID roomId,
        UUID senderId,
        String senderDisplayName,
        String content,
        MessageStatus status,
        Instant createdAt
) {
    public DurableMessage {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(senderDisplayName, "senderDisplayName");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
