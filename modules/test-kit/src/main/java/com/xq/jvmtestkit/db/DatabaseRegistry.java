package com.xq.jvmtestkit.db;

import java.util.Map;
import java.util.Objects;

/** Named database clients for one XQ test invocation. */
public final class DatabaseRegistry implements AutoCloseable {
    private final Map<String, DatabaseClient> clients;
    public DatabaseRegistry(Map<String, DatabaseClient> clients) { this.clients = Map.copyOf(Objects.requireNonNull(clients, "clients")); }
    public DatabaseClient get(String name) {
        DatabaseClient client = clients.get(name);
        if (client == null) throw new IllegalArgumentException("Unknown database '" + name + "'; configured names=" + clients.keySet());
        return client;
    }
    public DatabaseClient database(String name) { return get(name); }
    public java.util.Set<String> names() { return clients.keySet(); }
    @Override public void close() { clients.values().forEach(DatabaseClient::close); }
}
