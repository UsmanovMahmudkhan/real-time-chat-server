package io.github.usmanovmahmudkhan.realtimechat.core.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        UUID tenantId,
        String subject,
        String displayName,
        Set<TenantRole> roles
) {
    public AuthenticatedUser {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(displayName, "displayName");
        roles = Set.copyOf(roles);
    }
}
