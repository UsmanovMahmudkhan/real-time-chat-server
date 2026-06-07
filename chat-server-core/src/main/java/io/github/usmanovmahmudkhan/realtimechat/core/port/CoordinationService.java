package io.github.usmanovmahmudkhan.realtimechat.core.port;

import io.github.usmanovmahmudkhan.realtimechat.core.protocol.ServerEvent;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;

public interface CoordinationService extends AutoCloseable {
    void publish(ServerEvent event);
    AutoCloseable subscribe(UUID tenantId, UUID roomId, Consumer<ServerEvent> consumer);
    boolean allow(String dimension, String key, int limit, Duration window);
    void touchPresence(UUID tenantId, UUID roomId, UUID userId, Duration lease);
    boolean isReady();
    @Override void close();
}
