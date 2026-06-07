package io.github.usmanovmahmudkhan.realtimechat.postgres;

import io.github.usmanovmahmudkhan.realtimechat.core.domain.AuthenticatedUser;
import io.github.usmanovmahmudkhan.realtimechat.core.domain.TenantRole;
import io.github.usmanovmahmudkhan.realtimechat.core.port.IdentityRepository;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class JdbcIdentityRepository implements IdentityRepository {
    private final DataSource dataSource;

    public JdbcIdentityRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<AuthenticatedUser> findActiveIdentity(String issuer, String subject, UUID tenantId) {
        String sql = """
                select u.user_id, u.display_name, tm.roles
                from external_identities e
                join users u on u.user_id = e.user_id and u.active
                join tenant_memberships tm on tm.user_id = u.user_id and tm.tenant_id = ? and tm.active
                join tenants t on t.tenant_id = tm.tenant_id and t.active
                where e.issuer = ? and e.subject = ?
                """;
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setString(2, issuer);
            statement.setString(3, subject);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                String[] roles = (String[]) result.getArray("roles").getArray();
                return Optional.of(new AuthenticatedUser(
                        result.getObject("user_id", UUID.class), tenantId, subject,
                        result.getString("display_name"),
                        Arrays.stream(roles).map(TenantRole::valueOf).collect(Collectors.toSet())));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not resolve external identity", exception);
        }
    }
}
