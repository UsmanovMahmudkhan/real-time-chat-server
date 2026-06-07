package io.github.usmanovmahmudkhan.realtimechat.postgres;

import io.github.usmanovmahmudkhan.realtimechat.core.port.OriginRepository;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class JdbcOriginRepository implements OriginRepository {
    private final DataSource dataSource;

    public JdbcOriginRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Set<String> findAllowedOrigins(UUID tenantId) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "select allowed_origins from tenants where tenant_id = ? and active")) {
            statement.setObject(1, tenantId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return Set.of();
                }
                return Arrays.stream((String[]) result.getArray(1).getArray()).collect(Collectors.toUnmodifiableSet());
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load tenant origins", exception);
        }
    }
}
