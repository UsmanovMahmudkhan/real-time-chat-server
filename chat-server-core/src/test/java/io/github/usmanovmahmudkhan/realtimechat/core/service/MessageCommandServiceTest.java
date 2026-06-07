package io.github.usmanovmahmudkhan.realtimechat.core.service;

import io.github.usmanovmahmudkhan.realtimechat.core.domain.*;
import io.github.usmanovmahmudkhan.realtimechat.core.port.*;
import io.github.usmanovmahmudkhan.realtimechat.core.protocol.ServerEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageCommandServiceTest {
    @Test
    void persistsBeforePublishingAndDeduplicates() {
        UUID tenant = UUID.randomUUID();
        UUID room = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), tenant, "sub", "User",
                Set.of(TenantRole.MEMBER));
        FakeMessages repository = new FakeMessages();
        FakeCoordination coordination = new FakeCoordination();
        AccessRepository access = (ignored, ignoredTenant, ignoredRoom) ->
                Optional.of(new RoomAccess(tenant, room, RoomVisibility.PUBLIC, false, Set.of()));
        MessageCommandService service = new MessageCommandService(repository, access, coordination,
                new AuthorizationService(), 1000, 100);

        DurableMessage first = service.send(user, tenant, room, key, "hello", "correlation");
        DurableMessage duplicate = service.send(user, tenant, room, key, "hello", "correlation");

        assertEquals(first.messageId(), duplicate.messageId());
        assertEquals(1, repository.persistCount);
        assertEquals(0, coordination.publishCount);
    }

    private static final class FakeMessages implements MessageRepository {
        private final Map<UUID, DurableMessage> messages = new HashMap<>();
        private int persistCount;

        public DurableMessage persist(DurableMessage message, UUID key, String correlation) {
            persistCount++;
            messages.put(key, message);
            return message;
        }
        public Optional<DurableMessage> findByIdempotencyKey(UUID tenant, UUID sender, UUID key) {
            return Optional.ofNullable(messages.get(key));
        }
        public List<DurableMessage> findAfter(UUID tenant, UUID room, UUID after, int limit) { return List.of(); }
        public boolean isReady() { return true; }
    }

    private static final class FakeCoordination implements CoordinationService {
        private int publishCount;
        public void publish(ServerEvent event) { publishCount++; }
        public AutoCloseable subscribe(UUID tenant, UUID room, Consumer<ServerEvent> consumer) { return () -> { }; }
        public boolean allow(String dimension, String key, int limit, Duration window) { return true; }
        public void touchPresence(UUID tenant, UUID room, UUID user, Duration lease) { }
        public boolean isReady() { return true; }
        public void close() { }
    }
}
