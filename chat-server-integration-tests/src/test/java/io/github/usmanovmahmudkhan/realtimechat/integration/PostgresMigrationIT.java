package io.github.usmanovmahmudkhan.realtimechat.integration;

import io.github.usmanovmahmudkhan.realtimechat.postgres.PostgresDatabase;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void appliesEnterpriseSchemaAndReportsReady() {
        try (PostgresDatabase database = new PostgresDatabase(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword(), 4)) {
            database.migrate();
            assertTrue(database.isReady());
        }
    }
}
