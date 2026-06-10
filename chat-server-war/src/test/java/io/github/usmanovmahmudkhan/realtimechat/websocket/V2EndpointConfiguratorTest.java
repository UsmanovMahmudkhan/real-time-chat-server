package io.github.usmanovmahmudkhan.realtimechat.websocket;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V2EndpointConfiguratorTest {

    @Test
    void acceptsExactHttpsOrigin() {
        assertTrue(V2EndpointConfigurator.isWellFormedHttpsOrigin("https://chat.example.com"));
        assertTrue(V2EndpointConfigurator.isWellFormedHttpsOrigin("https://chat.example.com:8443"));
    }

    @Test
    void rejectsInsecureOrMalformedOrigins() {
        assertFalse(V2EndpointConfigurator.isWellFormedHttpsOrigin("http://chat.example.com"));
        assertFalse(V2EndpointConfigurator.isWellFormedHttpsOrigin("https://*.example.com"));
        assertFalse(V2EndpointConfigurator.isWellFormedHttpsOrigin("https://"));
        assertFalse(V2EndpointConfigurator.isWellFormedHttpsOrigin("null"));
        assertFalse(V2EndpointConfigurator.isWellFormedHttpsOrigin("ws://chat.example.com"));
        assertFalse(V2EndpointConfigurator.isWellFormedHttpsOrigin("not a uri"));
    }

    @Test
    void extractsTenantIdFromHandshakePath() {
        UUID tenantId = UUID.fromString("018f6b75-9200-7d00-a000-000000000001");
        assertEquals(tenantId, V2EndpointConfigurator.tenantFromPath(
                "/v2/tenants/" + tenantId + "/rooms/" + UUID.randomUUID() + "/chat"));
    }

    @Test
    void rejectsPathsWithoutValidTenantSegment() {
        assertThrows(IllegalArgumentException.class,
                () -> V2EndpointConfigurator.tenantFromPath("/v2/rooms/abc/chat"));
        assertThrows(IllegalArgumentException.class,
                () -> V2EndpointConfigurator.tenantFromPath("/v2/tenants/not-a-uuid/rooms/abc/chat"));
        assertThrows(IllegalArgumentException.class,
                () -> V2EndpointConfigurator.tenantFromPath("/v2/tenants"));
    }
}
