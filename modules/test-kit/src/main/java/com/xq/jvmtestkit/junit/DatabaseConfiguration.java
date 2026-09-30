package com.xq.jvmtestkit.junit;

import com.xq.jvmtestkit.db.DatabaseClient;
import com.xq.jvmtestkit.db.DatabaseRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

final class DatabaseConfiguration {
    static DatabaseRegistry load(ClassLoader loader) {
        if (loader == null) loader = DatabaseConfiguration.class.getClassLoader();
        Properties properties = new Properties();
        try {
            Enumeration<URL> files = loader.getResources("xq.properties");
            if (!files.hasMoreElements()) return new DatabaseRegistry(Map.of());
            URL file = files.nextElement();
            if (files.hasMoreElements()) throw new IllegalStateException("Multiple classpath configuration resources /xq.properties found");
            try (InputStream input = file.openStream()) { properties.load(input); }
        } catch (IOException exception) { throw new IllegalStateException("Could not read /xq.properties", exception); }
        Map<String, Map<String, String>> values = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("xq.database.")) continue;
            String remainder = key.substring("xq.database.".length());
            int split = remainder.indexOf('.');
            if (split <= 0 || split == remainder.length() - 1) throw new IllegalStateException("Invalid database property name in /xq.properties");
            values.computeIfAbsent(remainder.substring(0, split), ignored -> new LinkedHashMap<>())
                    .put(remainder.substring(split + 1), resolve(properties.getProperty(key)));
        }
        Map<String, DatabaseClient> clients = new LinkedHashMap<>();
        values.forEach((name, config) -> {
            String url = required(config, name, "url");
            String username = required(config, name, "username");
            String password = required(config, name, "password");
            clients.put(name, new DatabaseClient(name, url, username, password));
        });
        return new DatabaseRegistry(clients);
    }
    private static String required(Map<String, String> values, String database, String field) {
        String value = values.get(field);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing xq.database." + database + "." + field + " in /xq.properties");
        return value;
    }
    private static String resolve(String value) {
        if (value != null && value.startsWith("-env ")) {
            String name = value.substring(5).trim();
            if (name.isEmpty()) throw new IllegalStateException("Environment variable name must not be blank");
            String resolved = System.getenv(name);
            if (resolved == null || resolved.isBlank()) throw new IllegalStateException("Configured database environment variable is missing");
            return resolved;
        }
        return value;
    }
}
