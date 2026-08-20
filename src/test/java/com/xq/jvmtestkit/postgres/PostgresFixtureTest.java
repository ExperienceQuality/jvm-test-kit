package com.xq.jvmtestkit.postgres;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresFixtureTest {
    @Test
    void defaultsUseDigestPinnedImage() {
        PostgresFixtureConfig config = PostgresFixtureConfig.defaults();
        assertTrue(config.imageName().contains("@sha256:"));
        assertEquals(PostgresFixtureConfig.APPROVED_POSTGRES_16_DIGEST,
                config.imageName().substring(config.imageName().indexOf('@') + 1));
        assertFalse(config.sanitizedDiagnostics().contains("jvmtestkit-secret"));
    }

    @Test
    void rejectsMovingTags() {
        assertEquals(
                "imageName must be digest-pinned",
                org.junit.jupiter.api.Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> PostgresFixtureConfig.builder().imageName("postgres:16").build()
                ).getMessage());
    }

    @Test
    @EnabledIf("com.xq.jvmtestkit.postgres.DockerConditions#available")
    void startsProvidesJdbcAccessAndCleansUp() throws Exception {
        PostgresFixtureConfig config = PostgresFixtureConfig.builder()
                .databaseName("fixture_db")
                .username("fixture_user")
                .password("fixture-pass")
                .build();

        try (PostgresFixture fixture = new PostgresFixture(config)) {
            fixture.start();
            fixture.executeSql("CREATE TABLE IF NOT EXISTS pets (id INT PRIMARY KEY, name TEXT NOT NULL)");
            fixture.executeSql("INSERT INTO pets (id, name) VALUES (1, 'mochi') ON CONFLICT (id) DO NOTHING");

            try (var connection = fixture.openConnection();
                 var statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT name FROM pets WHERE id = 1")) {
                assertTrue(resultSet.next());
                assertEquals("mochi", resultSet.getString(1));
            }

            assertTrue(fixture.sanitizedDiagnostics().contains("status=running"));
            assertFalse(fixture.sanitizedDiagnostics().contains("fixture-pass"));
        }
    }
}
