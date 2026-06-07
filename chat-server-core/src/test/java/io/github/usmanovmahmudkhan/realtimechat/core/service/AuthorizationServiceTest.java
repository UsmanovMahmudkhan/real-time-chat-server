package io.github.usmanovmahmudkhan.realtimechat.core.service;

import io.github.usmanovmahmudkhan.realtimechat.core.domain.*;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationServiceTest {
    private final AuthorizationService service = new AuthorizationService();
    private final UUID tenant = UUID.randomUUID();
    private final UUID room = UUID.randomUUID();

    @Test
    void enforcesTenantBoundary() {
        assertFalse(service.canJoin(user(UUID.randomUUID(), TenantRole.OWNER),
                new RoomAccess(tenant, room, RoomVisibility.PUBLIC, true, Set.of())));
    }

    @Test
    void allowsPublicTenantMemberButReadOnlyCannotSend() {
        var access = new RoomAccess(tenant, room, RoomVisibility.PUBLIC, false, Set.of());
        assertTrue(service.canJoin(user(tenant, TenantRole.READ_ONLY), access));
        assertFalse(service.canSend(user(tenant, TenantRole.READ_ONLY), access));
    }

    @Test
    void restrictedRoomRequiresMembershipAndRole() {
        var access = new RoomAccess(tenant, room, RoomVisibility.RESTRICTED, true, Set.of(TenantRole.MEMBER));
        assertTrue(service.canJoin(user(tenant, TenantRole.MEMBER), access));
        assertFalse(service.canJoin(user(tenant, TenantRole.READ_ONLY), access));
    }

    private AuthenticatedUser user(UUID tenantId, TenantRole role) {
        return new AuthenticatedUser(UUID.randomUUID(), tenantId, "subject", "User", Set.of(role));
    }
}
