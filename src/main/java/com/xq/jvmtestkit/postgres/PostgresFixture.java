package com.xq.jvmtestkit.postgres;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Disposable PostgreSQL fixture with generated ports and idempotent cleanup.
 */
public final class PostgresFixture implements AutoCloseable {
    private final PostgresFixtureConfig config;
    private final PostgreSQLContainer<?> container;

    public PostgresFixture(PostgresFixtureConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.container = new PostgreSQLContainer<>(DockerImageName.parse(config.imageName())
                .asCompatibleSubstituteFor("postgres"))
                .withDatabaseName(config.databaseName())
                .withUsername(config.username())
                .withPassword(config.password());
    }

    public PostgresFixtureConfig config() {
        return config;
    }

    public void start() {
        try {
            container.start();
        } catch (RuntimeException exception) {
            throw new PostgresFixtureException(
                    "PostgreSQL fixture failed to start; " + config.sanitizedDiagnostics(), exception);
        }
    }

    public String jdbcUrl() {
        ensureStarted();
        return container.getJdbcUrl();
    }

    public Connection openConnection() throws SQLException {
        ensureStarted();
        return DriverManager.getConnection(jdbcUrl(), config.username(), config.password());
    }

    public void executeSql(String sql) throws SQLException {
        Objects.requireNonNull(sql, "sql");
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @Override
    public void close() {
        container.stop();
    }

    public String sanitizedDiagnostics() {
        if (!container.isRunning()) {
            return "status=stopped; " + config.sanitizedDiagnostics();
        }
        return "status=running; host=<mapped>; port=<mapped>; " + config.sanitizedDiagnostics();
    }

    private void ensureStarted() {
        if (!container.isRunning()) {
            throw new IllegalStateException("PostgreSQL fixture is not started");
        }
    }

    public static final class PostgresFixtureException extends RuntimeException {
        PostgresFixtureException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
