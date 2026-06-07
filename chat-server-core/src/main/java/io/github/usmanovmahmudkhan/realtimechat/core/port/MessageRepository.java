package io.github.usmanovmahmudkhan.realtimechat.core.port;

import io.github.usmanovmahmudkhan.realtimechat.core.domain.DurableMessage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository {
    DurableMessage persist(DurableMessage message, UUID idempotencyKey, String correlationId);
    Optional<DurableMessage> findByIdempotencyKey(UUID tenantId, UUID senderId, UUID idempotencyKey);
    List<DurableMessage> findAfter(UUID tenantId, UUID roomId, UUID afterMessageId, int limit);
    boolean isReady();
}
