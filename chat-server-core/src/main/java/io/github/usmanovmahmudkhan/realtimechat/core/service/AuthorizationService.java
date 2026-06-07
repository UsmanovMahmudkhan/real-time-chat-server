package io.github.usmanovmahmudkhan.realtimechat.core.service;

import io.github.usmanovmahmudkhan.realtimechat.core.domain.AuthenticatedUser;
import io.github.usmanovmahmudkhan.realtimechat.core.domain.RoomAccess;
import io.github.usmanovmahmudkhan.realtimechat.core.domain.RoomVisibility;
import io.github.usmanovmahmudkhan.realtimechat.core.domain.TenantRole;

public final class AuthorizationService {
    public boolean canJoin(AuthenticatedUser user, RoomAccess access) {
        if (!user.tenantId().equals(access.tenantId())) {
            return false;
        }
        if (isAdministrator(user)) {
            return true;
        }
        return switch (access.visibility()) {
            case PUBLIC -> true;
            case PRIVATE -> access.explicitMember();
            case RESTRICTED -> access.explicitMember()
                    && !access.requiredRoles().isEmpty()
                    && user.roles().stream().anyMatch(access.requiredRoles()::contains);
        };
    }

    public boolean canSend(AuthenticatedUser user, RoomAccess access) {
        return canJoin(user, access) && !user.roles().contains(TenantRole.READ_ONLY);
    }

    public boolean canModerate(AuthenticatedUser user) {
        return user.roles().contains(TenantRole.OWNER)
                || user.roles().contains(TenantRole.ADMIN)
                || user.roles().contains(TenantRole.MODERATOR);
    }

    public boolean canAdminister(AuthenticatedUser user) {
        return user.roles().contains(TenantRole.OWNER) || user.roles().contains(TenantRole.ADMIN);
    }

    private boolean isAdministrator(AuthenticatedUser user) {
        return canAdminister(user) || user.roles().contains(TenantRole.MODERATOR);
    }
}
