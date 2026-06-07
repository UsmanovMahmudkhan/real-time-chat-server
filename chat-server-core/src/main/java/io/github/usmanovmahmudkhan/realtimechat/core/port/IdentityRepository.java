package io.github.usmanovmahmudkhan.realtimechat.core.port;

import io.github.usmanovmahmudkhan.realtimechat.core.domain.AuthenticatedUser;

import java.util.Optional;
import java.util.UUID;

public interface IdentityRepository {
    Optional<AuthenticatedUser> findActiveIdentity(String issuer, String subject, UUID tenantId);
}
