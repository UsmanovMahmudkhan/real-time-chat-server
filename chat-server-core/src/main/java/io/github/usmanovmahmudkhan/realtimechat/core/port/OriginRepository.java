package io.github.usmanovmahmudkhan.realtimechat.core.port;

import java.util.Set;
import java.util.UUID;

public interface OriginRepository {
    Set<String> findAllowedOrigins(UUID tenantId);
}
