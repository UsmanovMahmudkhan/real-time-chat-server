package io.github.usmanovmahmudkhan.realtimechat.postgres;

import io.github.usmanovmahmudkhan.realtimechat.core.domain.DurableMessage;
import io.github.usmanovmahmudkhan.realtimechat.core.domain.MessageStatus;
import io.github.usmanovmahmudkhan.realtimechat.core.port.CoordinationService;
import io.github.usmanovmahmudkhan.realtimechat.core.protocol.ServerEvent;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class JdbcOutboxPublisher {
    private final DataSource dataSource;
    private final CoordinationService coordination;

    public JdbcOutboxPublisher(DataSource dataSource, CoordinationService coordination) {
        this.dataSource = dataSource;
        this.coordination = coordination;
    }

    public int publishBatch(int batchSize) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            List<OutboxItem> items = lockBatch(connection, batchSize);
            int published = 0;
            for (OutboxItem item : items) {
                try {
                    coordination.publish(ServerEvent.chat(item.message(), item.correlationId()));
                    markPublished(connection, item.eventId());
                    published++;
                } catch (RuntimeException exception) {
                    markAttempt(connection, item.eventId());
                }
            }
            connection.commit();
            return published;
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not publish outbox batch", exception);
        }
    }

    public long backlogCount() {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("select count(*) from outbox_events where published_at is null");
             var result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        } catch (SQLException exception) {
            return -1;
        }
    }

    private List<OutboxItem> lockBatch(Connection connection, int batchSize) throws SQLException {
        String sql = """
                select o.event_id, o.correlation_id, m.*
                from outbox_events o
                join messages m on m.tenant_id = o.tenant_id and m.message_id = o.aggregate_id
                where o.published_at is null and o.attempts < 100
                order by o.created_at
                for update of o skip locked
                limit ?
                """;
        List<OutboxItem> items = new ArrayList<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, batchSize);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    items.add(new OutboxItem(result.getObject("event_id", UUID.class),
                            result.getString("correlation_id"), map(result)));
                }
            }
        }
        return items;
    }

    private void markPublished(Connection connection, UUID eventId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "update outbox_events set published_at = now(), attempts = attempts + 1 where event_id = ?")) {
            statement.setObject(1, eventId);
            statement.executeUpdate();
        }
    }

    private void markAttempt(Connection connection, UUID eventId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "update outbox_events set attempts = attempts + 1 where event_id = ?")) {
            statement.setObject(1, eventId);
            statement.executeUpdate();
        }
    }

    private DurableMessage map(ResultSet result) throws SQLException {
        return new DurableMessage(result.getObject("message_id", UUID.class),
                result.getObject("tenant_id", UUID.class), result.getObject("room_id", UUID.class),
                result.getObject("sender_id", UUID.class), result.getString("sender_display_name"),
                result.getString("content"), MessageStatus.valueOf(result.getString("status")),
                result.getTimestamp("created_at").toInstant());
    }

    private record OutboxItem(UUID eventId, String correlationId, DurableMessage message) {
    }
}
