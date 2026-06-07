package io.github.usmanovmahmudkhan.realtimechat.integration;

import io.github.usmanovmahmudkhan.realtimechat.redis.RedisCoordinationService;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class RedisCoordinationIT {
    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    @Test
    void enforcesDistributedLimit() {
        String uri = "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
        try (RedisCoordinationService service = new RedisCoordinationService(uri, "test")) {
            assertTrue(service.isReady());
            assertTrue(service.allow("test", "key", 1, Duration.ofSeconds(5)));
            assertFalse(service.allow("test", "key", 1, Duration.ofSeconds(5)));
        }
    }
}
