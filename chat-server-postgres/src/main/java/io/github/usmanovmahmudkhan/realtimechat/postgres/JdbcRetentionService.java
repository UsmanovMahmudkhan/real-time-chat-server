package io.github.usmanovmahmudkhan.realtimechat.postgres;

import javax.sql.DataSource;
import java.sql.SQLException;

public final class JdbcRetentionService {
    private final DataSource dataSource;

    public JdbcRetentionService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public int tombstoneExpiredMessages(int batchSize) {
        String sql = """
                with expired as (
                    select m.tenant_id, m.message_id
                    from messages m join retention_policies p on p.tenant_id = m.tenant_id
                    where m.status = 'ACTIVE'
                      and m.created_at < now() - make_interval(days => p.retention_days)
                      and not exists (
                        select 1 from legal_holds h where h.tenant_id = m.tenant_id and h.active
                          and (h.room_id is null or h.room_id = m.room_id)
                      )
                    order by m.created_at
                    limit ?
                    for update of m skip locked
                )
                update messages m set status = 'TOMBSTONED', content = '[removed by retention policy]',
                                      tombstoned_at = now()
                from expired e where m.tenant_id = e.tenant_id and m.message_id = e.message_id
                """;
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, batchSize);
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not execute retention policy", exception);
        }
    }
}
