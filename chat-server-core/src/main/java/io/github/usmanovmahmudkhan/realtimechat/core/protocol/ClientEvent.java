package io.github.usmanovmahmudkhan.realtimechat.core.protocol;

import java.util.UUID;

public record ClientEvent(
        int protocolVersion,
        ClientEventType type,
        String correlationId,
        UUID idempotencyKey,
        UUID afterMessageId,
        String content
) {
}
