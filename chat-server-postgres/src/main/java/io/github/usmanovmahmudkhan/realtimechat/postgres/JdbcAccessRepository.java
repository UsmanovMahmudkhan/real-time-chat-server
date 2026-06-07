package io.github.usmanovmahmudkhan.realtimechat.postgres;

import io.github.usmanovmahmudkhan.realtimechat.core.domain.AuthenticatedUser;
import io.github.usmanovmahmudkhan.realtimechat.core.domain.RoomAccess;
import io.github.usmanovmahmudkhan.realtimechat.core.domain.RoomVisibility;
import io.github.usmanovmahmudkhan.realtimechat.core.domain.TenantRole;
import io.github.usmanovmahmudkhan.realtimechat.core.port.AccessRepository;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class JdbcAccessRepository implements AccessRepository {
    private final DataSource dataSource;

    public JdbcAccessRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<RoomAccess> findRoomAccess(AuthenticatedUser user, UUID tenantId, UUID roomId) {
        String sql = """
                select r.visibility, r.required_roles,
                       exists(select 1 from room_memberships rm
                              where rm.tenant_id = r.tenant_id and rm.room_id = r.room_id
                                and rm.user_id = ? and rm.active) as explicit_member
                from rooms r join tenant_memberships tm
                  on tm.tenant_id = r.tenant_id and tm.user_id = ? and tm.active
                where r.tenant_id = ? and r.room_id = ? and r.active
                """;
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, user.userId());
            statement.setObject(2, user.userId());
            statement.setObject(3, tenantId);
            statement.setObject(4, roomId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                String[] required = (String[]) result.getArray("required_roles").getArray();
                Set<TenantRole> roles = Arrays.stream(required).map(TenantRole::valueOf).collect(Collectors.toSet());
                return Optional.of(new RoomAccess(tenantId, roomId,
                        RoomVisibility.valueOf(result.getString("visibility")),
                        result.getBoolean("explicit_member"), roles));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load room access", exception);
        }
    }
}
