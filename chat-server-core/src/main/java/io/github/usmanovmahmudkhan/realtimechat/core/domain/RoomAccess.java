package io.github.usmanovmahmudkhan.realtimechat.core.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record RoomAccess(
        UUID tenantId,
        UUID roomId,
        RoomVisibility visibility,
        boolean explicitMember,
        Set<TenantRole> requiredRoles
) {
    public RoomAccess {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(visibility, "visibility");
        requiredRoles = Set.copyOf(requiredRoles);
    }
}
