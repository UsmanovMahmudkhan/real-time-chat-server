package io.github.usmanovmahmudkhan.realtimechat.core.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AuditEvent(
        UUID eventId,
        UUID tenantId,
        UUID actorId,
        String action,
        String targetType,
        String targetId,
        String result,
        String correlationId,
        Map<String, String> metadata,
        Instant createdAt
) {
    public AuditEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(correlationId, "correlationId");
        metadata = Map.copyOf(metadata);
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
