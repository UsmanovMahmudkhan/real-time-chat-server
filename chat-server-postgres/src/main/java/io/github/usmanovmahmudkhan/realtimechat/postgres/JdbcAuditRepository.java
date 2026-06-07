package io.github.usmanovmahmudkhan.realtimechat.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.usmanovmahmudkhan.realtimechat.core.domain.AuditEvent;
import io.github.usmanovmahmudkhan.realtimechat.core.port.AuditRepository;

import javax.sql.DataSource;
import java.sql.SQLException;

public final class JdbcAuditRepository implements AuditRepository {
    private final DataSource dataSource;
    private final ObjectMapper mapper = new ObjectMapper();

    public JdbcAuditRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void append(AuditEvent event) {
        String sql = """
                insert into audit_events(event_id, tenant_id, actor_id, action, target_type, target_id,
                                         result, correlation_id, metadata, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """;
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, event.eventId());
            statement.setObject(2, event.tenantId());
            statement.setObject(3, event.actorId());
            statement.setString(4, event.action());
            statement.setString(5, event.targetType());
            statement.setString(6, event.targetId());
            statement.setString(7, event.result());
            statement.setString(8, event.correlationId());
            statement.setString(9, mapper.writeValueAsString(event.metadata()));
            statement.setObject(10, event.createdAt());
            statement.executeUpdate();
        } catch (SQLException | JsonProcessingException exception) {
            throw new IllegalStateException("Could not append audit event", exception);
        }
    }
}
