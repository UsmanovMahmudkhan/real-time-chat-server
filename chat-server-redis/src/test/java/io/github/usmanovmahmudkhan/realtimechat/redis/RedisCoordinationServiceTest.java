package io.github.usmanovmahmudkhan.realtimechat.redis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class RedisCoordinationServiceTest {
    @Test
    void rejectsUnsafeEnvironmentNamespaceBeforeConnecting() {
        assertThrows(IllegalArgumentException.class,
                () -> new RedisCoordinationService("redis://localhost:6379", "bad:environment"));
    }
}
