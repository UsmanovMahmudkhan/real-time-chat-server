package io.github.usmanovmahmudkhan.realtimechat.postgres;

import io.github.usmanovmahmudkhan.realtimechat.core.domain.AuthenticatedUser;
import io.github.usmanovmahmudkhan.realtimechat.core.domain.RoomVisibility;
import io.github.usmanovmahmudkhan.realtimechat.core.domain.TenantRole;
import io.github.usmanovmahmudkhan.realtimechat.core.protocol.ErrorCode;
import io.github.usmanovmahmudkhan.realtimechat.core.protocol.ProtocolException;
import io.github.usmanovmahmudkhan.realtimechat.core.util.UuidV7;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

public final class JdbcManagementService {
    private final DataSource dataSource;

    public JdbcManagementService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public UUID createRoom(AuthenticatedUser actor, UUID tenantId, String name, RoomVisibility visibility,
                           Set<TenantRole> requiredRoles, String correlationId) {
        requireAdmin(actor, tenantId);
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new IllegalArgumentException("Room name is invalid");
        }
        UUID roomId = UuidV7.next();
        transaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    insert into rooms(tenant_id, room_id, name, visibility, required_roles)
                    values (?, ?, ?, ?::room_visibility, ?)
                    """)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, roomId);
                statement.setString(3, name);
                statement.setString(4, visibility.name());
                statement.setArray(5, connection.createArrayOf("text",
                        requiredRoles.stream().map(Enum::name).toArray(String[]::new)));
                statement.executeUpdate();
            }
            audit(connection, actor, tenantId, "ROOM_CREATE", "ROOM", roomId.toString(), correlationId);
        });
        return roomId;
    }

    public void upsertTenantMembership(AuthenticatedUser actor, UUID tenantId, UUID userId,
                                       Set<TenantRole> roles, String correlationId) {
        requireAdmin(actor, tenantId);
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }
        transaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    insert into tenant_memberships(tenant_id, user_id, roles, active)
                    values (?, ?, ?::tenant_role[], true)
                    on conflict (tenant_id, user_id) do update set roles = excluded.roles, active = true
                    """)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, userId);
                statement.setArray(3, connection.createArrayOf("text",
                        roles.stream().map(Enum::name).toArray(String[]::new)));
                statement.executeUpdate();
            }
            audit(connection, actor, tenantId, "TENANT_MEMBERSHIP_UPSERT", "USER", userId.toString(), correlationId);
        });
    }

    public void upsertRoomMembership(AuthenticatedUser actor, UUID tenantId, UUID roomId,
                                     UUID userId, String correlationId) {
        requireAdmin(actor, tenantId);
        transaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    insert into room_memberships(tenant_id, room_id, user_id, active)
                    values (?, ?, ?, true)
                    on conflict (tenant_id, room_id, user_id) do update set active = true
                    """)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, roomId);
                statement.setObject(3, userId);
                statement.executeUpdate();
            }
            audit(connection, actor, tenantId, "ROOM_MEMBERSHIP_UPSERT", "ROOM", roomId.toString(), correlationId);
        });
    }

    public void setRetentionPolicy(AuthenticatedUser actor, UUID tenantId, int retentionDays, String correlationId) {
        requireAdmin(actor, tenantId);
        if (retentionDays < 1 || retentionDays > 3650) {
            throw new IllegalArgumentException("Retention days must be between 1 and 3650");
        }
        transaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    insert into retention_policies(tenant_id, retention_days, updated_at)
                    values (?, ?, now())
                    on conflict (tenant_id) do update
                    set retention_days = excluded.retention_days, updated_at = now()
                    """)) {
                statement.setObject(1, tenantId);
                statement.setInt(2, retentionDays);
                statement.executeUpdate();
            }
            audit(connection, actor, tenantId, "RETENTION_POLICY_SET", "TENANT", tenantId.toString(), correlationId);
        });
    }

    public UUID createLegalHold(AuthenticatedUser actor, UUID tenantId, UUID roomId,
                                String reason, String correlationId) {
        requireAdmin(actor, tenantId);
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new IllegalArgumentException("Legal hold reason is invalid");
        }
        UUID holdId = UuidV7.next();
        transaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    insert into legal_holds(hold_id, tenant_id, room_id, reason) values (?, ?, ?, ?)
                    """)) {
                statement.setObject(1, holdId);
                statement.setObject(2, tenantId);
                statement.setObject(3, roomId);
                statement.setString(4, reason);
                statement.executeUpdate();
            }
            audit(connection, actor, tenantId, "LEGAL_HOLD_CREATE", "LEGAL_HOLD", holdId.toString(), correlationId);
        });
        return holdId;
    }

    public void tombstoneMessage(AuthenticatedUser actor, UUID tenantId, UUID messageId, String correlationId) {
        requireModerator(actor, tenantId);
        transaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    update messages set status = 'TOMBSTONED', content = '[removed by moderator]',
                                        tombstoned_at = now()
                    where tenant_id = ? and message_id = ? and status = 'ACTIVE'
                    """)) {
                statement.setObject(1, tenantId);
                statement.setObject(2, messageId);
                if (statement.executeUpdate() != 1) {
                    throw new IllegalArgumentException("Active message was not found");
                }
            }
            audit(connection, actor, tenantId, "MESSAGE_TOMBSTONE", "MESSAGE", messageId.toString(), correlationId);
        });
    }

    private void requireAdmin(AuthenticatedUser actor, UUID tenantId) {
        requireTenant(actor, tenantId);
        if (!actor.roles().contains(TenantRole.OWNER) && !actor.roles().contains(TenantRole.ADMIN)) {
            throw new ProtocolException(ErrorCode.UNAUTHORIZED, "Administrator permission required");
        }
    }

    private void requireModerator(AuthenticatedUser actor, UUID tenantId) {
        requireTenant(actor, tenantId);
        if (actor.roles().stream().noneMatch(role ->
                role == TenantRole.OWNER || role == TenantRole.ADMIN || role == TenantRole.MODERATOR)) {
            throw new ProtocolException(ErrorCode.UNAUTHORIZED, "Moderator permission required");
        }
    }

    private void requireTenant(AuthenticatedUser actor, UUID tenantId) {
        if (!actor.tenantId().equals(tenantId)) {
            throw new ProtocolException(ErrorCode.TENANT_MISMATCH, "Tenant mismatch");
        }
    }

    private void audit(Connection connection, AuthenticatedUser actor, UUID tenantId, String action,
                       String targetType, String targetId, String correlationId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                insert into audit_events(event_id, tenant_id, actor_id, action, target_type, target_id,
                                         result, correlation_id, metadata, created_at)
                values (?, ?, ?, ?, ?, ?, 'SUCCESS', ?, '{}'::jsonb, now())
                """)) {
            statement.setObject(1, UuidV7.next());
            statement.setObject(2, tenantId);
            statement.setObject(3, actor.userId());
            statement.setString(4, action);
            statement.setString(5, targetType);
            statement.setString(6, targetId);
            statement.setString(7, correlationId);
            statement.executeUpdate();
        }
    }

    private void transaction(SqlAction action) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                action.run(connection);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                if (exception instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Management transaction failed", exception);
        }
    }

    @FunctionalInterface
    private interface SqlAction {
        void run(Connection connection) throws SQLException;
    }
}
