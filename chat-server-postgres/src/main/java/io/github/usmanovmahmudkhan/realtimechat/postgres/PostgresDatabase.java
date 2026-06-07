package io.github.usmanovmahmudkhan.realtimechat.postgres;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

public final class PostgresDatabase implements AutoCloseable {
    private final HikariDataSource dataSource;

    public PostgresDatabase(String jdbcUrl, String username, String password, int maximumPoolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(Math.min(2, maximumPoolSize));
        config.setConnectionTimeout(5_000);
        config.setValidationTimeout(2_000);
        config.setPoolName("real-time-chat-postgres");
        this.dataSource = new HikariDataSource(config);
    }

    public void migrate() {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }

    public HikariDataSource dataSource() {
        return dataSource;
    }

    public boolean isReady() {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("select 1");
             var result = statement.executeQuery()) {
            return result.next() && result.getInt(1) == 1;
        } catch (Exception exception) {
            return false;
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
