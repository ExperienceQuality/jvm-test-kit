package com.xq.jvmtestkit.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Named, invocation-scoped JDBC client. The consumer owns SQL and schema types. */
public final class DatabaseClient implements AutoCloseable {
    private final String name;
    private final String url;
    private final String username;
    private final String password;
    private final AtomicBoolean closed = new AtomicBoolean();

    public DatabaseClient(String name, String url, String username, String password) {
        this.name = require(name, "name");
        this.url = require(url, "url");
        this.username = require(username, "username");
        this.password = Objects.requireNonNull(password, "password");
    }

    public String name() { return name; }

    public <T> T withConnection(ConnectionWork<T> work) {
        ensureOpen();
        Objects.requireNonNull(work, "work");
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            return work.apply(connection);
        } catch (SQLException exception) {
            throw failure("Database connection failed", exception);
        } catch (RuntimeException | Error exception) {
            throw new IllegalStateException("Database connection callback failed; remote details are redacted");
        }
    }

    public <T> T transaction(TransactionWork<T> work) {
        ensureOpen();
        Objects.requireNonNull(work, "work");
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (Throwable failure) {
                IllegalStateException sanitized = new IllegalStateException(
                        "Database transaction callback failed; remote details are redacted");
                try { connection.rollback(); }
                catch (SQLException rollbackFailure) {
                    sanitized.addSuppressed(new IllegalStateException(
                            "Database transaction rollback failed; remote details are redacted"));
                }
                throw sanitized;
            }
        } catch (SQLException exception) {
            throw failure("Database transaction failed", exception);
        } catch (RuntimeException | Error exception) {
            throw exception;
        }
    }

    @Override public void close() { closed.set(true); }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("Database client '" + name + "' is closed");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    private static IllegalStateException failure(String message, SQLException cause) {
        return new IllegalStateException(message + "; remote details are redacted");
    }

    @FunctionalInterface public interface ConnectionWork<T> { T apply(Connection connection) throws SQLException; }
    @FunctionalInterface public interface TransactionWork<T> { T apply(Connection connection) throws Exception; }
}
