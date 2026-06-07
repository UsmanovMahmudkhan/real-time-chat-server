package io.github.usmanovmahmudkhan.realtimechat.postgres;

import io.github.usmanovmahmudkhan.realtimechat.core.domain.DurableMessage;
import io.github.usmanovmahmudkhan.realtimechat.core.domain.MessageStatus;
import io.github.usmanovmahmudkhan.realtimechat.core.port.MessageRepository;
import io.github.usmanovmahmudkhan.realtimechat.core.util.UuidV7;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcMessageRepository implements MessageRepository {
    private final DataSource dataSource;

    public JdbcMessageRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public DurableMessage persist(DurableMessage message, UUID idempotencyKey, String correlationId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                insertMessage(connection, message);
                insertIdempotencyKey(connection, message, idempotencyKey);
                insertOutbox(connection, message, correlationId);
                connection.commit();
                return message;
            } catch (SQLException exception) {
                connection.rollback();
                Optional<DurableMessage> duplicate =
                        findByIdempotencyKey(message.tenantId(), message.senderId(), idempotencyKey);
                if (duplicate.isPresent()) {
                    return duplicate.get();
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not persist message transaction", exception);
        }
    }

    @Override
    public Optional<DurableMessage> findByIdempotencyKey(UUID tenantId, UUID senderId, UUID idempotencyKey) {
        String sql = """
                select m.* from message_idempotency_keys i
                join messages m on m.tenant_id = i.tenant_id and m.message_id = i.message_id
                where i.tenant_id = ? and i.sender_id = ? and i.idempotency_key = ?
                """;
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, senderId);
            statement.setObject(3, idempotencyKey);
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not find idempotent message", exception);
        }
    }

    @Override
    public List<DurableMessage> findAfter(UUID tenantId, UUID roomId, UUID afterMessageId, int limit) {
        String sql = """
                select m.* from messages m
                where m.tenant_id = ? and m.room_id = ?
                  and (?::uuid is null or m.created_at > (
                    select created_at from messages where tenant_id = ? and message_id = ?
                  ))
                order by m.created_at, m.message_id
                limit ?
                """;
        List<DurableMessage> messages = new ArrayList<>();
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, roomId);
            statement.setObject(3, afterMessageId);
            statement.setObject(4, tenantId);
            statement.setObject(5, afterMessageId);
            statement.setInt(6, limit);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    messages.add(map(result));
                }
            }
            return List.copyOf(messages);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load message history", exception);
        }
    }

    @Override
    public boolean isReady() {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("select 1");
             var result = statement.executeQuery()) {
            return result.next();
        } catch (SQLException exception) {
            return false;
        }
    }

    private void insertMessage(Connection connection, DurableMessage message) throws SQLException {
        String sql = """
                insert into messages(message_id, tenant_id, room_id, sender_id, sender_display_name,
                                     content, status, created_at)
                values (?, ?, ?, ?, ?, ?, ?::message_status, ?)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, message.messageId());
            statement.setObject(2, message.tenantId());
            statement.setObject(3, message.roomId());
            statement.setObject(4, message.senderId());
            statement.setString(5, message.senderDisplayName());
            statement.setString(6, message.content());
            statement.setString(7, message.status().name());
            statement.setObject(8, message.createdAt());
            statement.executeUpdate();
        }
    }

    private void insertIdempotencyKey(Connection connection, DurableMessage message, UUID key) throws SQLException {
        String sql = """
                insert into message_idempotency_keys(tenant_id, sender_id, idempotency_key, message_id, created_at)
                values (?, ?, ?, ?, ?)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, message.tenantId());
            statement.setObject(2, message.senderId());
            statement.setObject(3, key);
            statement.setObject(4, message.messageId());
            statement.setObject(5, message.createdAt());
            statement.executeUpdate();
        }
    }

    private void insertOutbox(Connection connection, DurableMessage message, String correlationId) throws SQLException {
        String sql = """
                insert into outbox_events(event_id, tenant_id, aggregate_type, aggregate_id, event_type,
                                          payload_reference, correlation_id, created_at)
                values (?, ?, 'MESSAGE', ?, 'MESSAGE_CREATED', ?, ?, ?)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UuidV7.next());
            statement.setObject(2, message.tenantId());
            statement.setObject(3, message.messageId());
            statement.setString(4, message.messageId().toString());
            statement.setString(5, correlationId);
            statement.setObject(6, message.createdAt());
            statement.executeUpdate();
        }
    }

    private DurableMessage map(ResultSet result) throws SQLException {
        return new DurableMessage(
                result.getObject("message_id", UUID.class),
                result.getObject("tenant_id", UUID.class),
                result.getObject("room_id", UUID.class),
                result.getObject("sender_id", UUID.class),
                result.getString("sender_display_name"),
                result.getString("content"),
                MessageStatus.valueOf(result.getString("status")),
                result.getTimestamp("created_at").toInstant()
        );
    }
}
