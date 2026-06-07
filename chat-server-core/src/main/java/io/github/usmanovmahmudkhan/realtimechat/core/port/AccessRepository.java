package io.github.usmanovmahmudkhan.realtimechat.core.port;

import io.github.usmanovmahmudkhan.realtimechat.core.domain.AuthenticatedUser;
import io.github.usmanovmahmudkhan.realtimechat.core.domain.RoomAccess;

import java.util.Optional;
import java.util.UUID;

public interface AccessRepository {
    Optional<RoomAccess> findRoomAccess(AuthenticatedUser user, UUID tenantId, UUID roomId);
}
