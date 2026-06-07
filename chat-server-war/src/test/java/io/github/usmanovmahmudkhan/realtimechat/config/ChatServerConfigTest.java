package io.github.usmanovmahmudkhan.realtimechat.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatServerConfigTest {
    private static final List<String> PROPERTIES = List.of(
            "realtimechat.environment", "realtimechat.jdbc.url", "realtimechat.database.username",
            "realtimechat.database.password", "realtimechat.redis.uri", "realtimechat.oidc.issuers",
            "realtimechat.oidc.audience", "realtimechat.allowed.origins"
    );

    @AfterEach
    void clearProperties() {
        PROPERTIES.forEach(System::clearProperty);
    }

    @Test
    void loadsMultipleIssuersAndExactOrigins() {
        configure();
        System.setProperty("realtimechat.oidc.issuers", "https://id-one.example.com,https://id-two.example.com");
        System.setProperty("realtimechat.allowed.origins", "https://chat.example.com");

        ChatServerConfig config = ChatServerConfig.load();

        assertEquals(2, config.oidcIssuers().size());
        assertEquals(1, config.globalAllowedOrigins().size());
    }

    @Test
    void rejectsWildcardOrigin() {
        configure();
        System.setProperty("realtimechat.allowed.origins", "https://*.example.com");

        assertThrows(IllegalStateException.class, ChatServerConfig::load);
    }

    private void configure() {
        System.setProperty("realtimechat.environment", "test");
        System.setProperty("realtimechat.jdbc.url", "jdbc:postgresql://localhost/test");
        System.setProperty("realtimechat.database.username", "test");
        System.setProperty("realtimechat.database.password", "secret");
        System.setProperty("realtimechat.redis.uri", "redis://localhost:6379");
        System.setProperty("realtimechat.oidc.issuers", "https://identity.example.com");
        System.setProperty("realtimechat.oidc.audience", "chat");
    }
}
