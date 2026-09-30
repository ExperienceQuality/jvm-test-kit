package com.xq.jvmtestkit.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseClientContractTest {
    private final FakeDriver driver = new FakeDriver();

    @BeforeEach void register() throws SQLException { DriverManager.registerDriver(driver); }
    @AfterEach void unregister() throws SQLException { DriverManager.deregisterDriver(driver); }

    @Test void scopesAndClosesConnectionOnSuccessAndFailure() {
        DatabaseClient client = new DatabaseClient("main", "jdbc:fake:test", "user", "secret");
        assertEquals("ok", client.withConnection(connection -> "ok"));
        assertEquals(1, driver.closed.get());
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> client.withConnection(connection -> { throw new SQLException("secret"); }));
        assertRedacted(failure);
        assertEquals(2, driver.closed.get());

        IllegalStateException runtimeFailure = assertThrows(IllegalStateException.class,
                () -> client.withConnection(connection -> { throw new IllegalStateException("secret"); }));
        assertRedacted(runtimeFailure);
        assertEquals(3, driver.closed.get());
    }

    private static void assertRedacted(Throwable failure) {
        assertFalse(failure.toString().contains("secret"));
        assertNull(failure.getCause());
        for (Throwable suppressed : failure.getSuppressed()) assertRedacted(suppressed);
        for (StackTraceElement frame : failure.getStackTrace()) assertFalse(frame.toString().contains("secret"));
    }

    @Test void commitsSuccessfulTransactionAndRollsBackFailures() {
        DatabaseClient client = new DatabaseClient("main", "jdbc:fake:test", "user", "secret");
        Integer result = client.transaction(connection -> Integer.valueOf(3));
        assertEquals(Integer.valueOf(3), result);
        assertEquals(1, driver.commits.get());
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> client.transaction(connection -> { throw new IllegalArgumentException("secret"); }));
        assertFalse(failure.getMessage().contains("secret"));
        assertNull(failure.getCause());
        assertRedacted(failure);
        assertEquals(1, driver.rollbacks.get());
    }

    @Test void connectionSetupFailureIsRedacted() {
        driver.failConnect = true;
        DatabaseClient client = new DatabaseClient("main", "jdbc:fake:test", "user", "secret");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> client.withConnection(connection -> null));
        assertRedacted(failure);
    }

    @Test void rollbackFailureIsSuppressedAndClientCannotBeReusedAfterClose() {
        driver.failRollback = true;
        DatabaseClient client = new DatabaseClient("main", "jdbc:fake:test", "user", "secret");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> client.transaction(connection -> { throw new IllegalArgumentException("secret"); }));
        assertEquals(1, failure.getSuppressed().length);
        assertFalse(failure.getMessage().contains("secret"));
        assertNull(failure.getSuppressed()[0].getCause());
        assertFalse(failure.getSuppressed()[0].getMessage().contains("secret"));
        assertRedacted(failure);
        client.close();
        assertThrows(IllegalStateException.class, () -> client.withConnection(connection -> null));
    }

    @Test void registryRejectsUnknownNamesAndClosesClients() {
        DatabaseClient client = new DatabaseClient("main", "jdbc:fake:test", "user", "secret");
        DatabaseRegistry registry = new DatabaseRegistry(java.util.Map.of("main", client));
        assertSame(client, registry.get("main"));
        assertThrows(IllegalArgumentException.class, () -> registry.get("other"));
        registry.close();
        assertThrows(IllegalStateException.class, () -> client.withConnection(connection -> null));
    }

    private static final class FakeDriver implements Driver {
        final AtomicInteger closed = new AtomicInteger();
        final AtomicInteger commits = new AtomicInteger();
        final AtomicInteger rollbacks = new AtomicInteger();
        boolean failRollback;
        boolean failConnect;
        @Override public Connection connect(String url, Properties info) throws SQLException {
            if (!acceptsURL(url)) return null;
            if (failConnect) throw new SQLException("connect secret");
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Connection.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "close" -> { closed.incrementAndGet(); return null; }
                    case "commit" -> { commits.incrementAndGet(); return null; }
                    case "rollback" -> { rollbacks.incrementAndGet(); if (failRollback) throw new SQLException("rollback secret"); return null; }
                    case "isClosed" -> { return false; }
                    case "setAutoCommit" -> { return null; }
                    default -> { return defaultValue(method.getReturnType()); }
                }
            });
        }
        @Override public boolean acceptsURL(String url) { return "jdbc:fake:test".equals(url); }
        @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return new DriverPropertyInfo[0]; }
        @Override public int getMajorVersion() { return 1; }
        @Override public int getMinorVersion() { return 0; }
        @Override public boolean jdbcCompliant() { return false; }
        @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { throw new SQLFeatureNotSupportedException(); }
        private static Object defaultValue(Class<?> type) { if (!type.isPrimitive()) return null; if (type == boolean.class) return false; if (type == char.class) return '\0'; return 0; }
    }
}
