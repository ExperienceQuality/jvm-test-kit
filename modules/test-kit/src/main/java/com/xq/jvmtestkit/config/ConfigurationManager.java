package com.xq.jvmtestkit.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Properties;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;

import static org.aeonbits.owner.Config.Sources;

/** Loads the test-kit configuration from the classpath. */
public final class ConfigurationManager {
    private static final Configuration configuration = ConfigFactory.create(Configuration.class);

    private ConfigurationManager() {}

    public static String getTestBaseUrl() {
        return configuration.testBaseUrl();
    }

    public static StubSettings loadStubSettings(ClassLoader loader) {
        if (loader == null) loader = ConfigurationManager.class.getClassLoader();
        try {
            Enumeration<URL> resources = loader.getResources("xq.yaml");
            if (!resources.hasMoreElements()) {
                throw new IllegalStateException("Missing classpath configuration resource /xq.yaml");
            }
            URL resource = resources.nextElement();
            if (resources.hasMoreElements()) {
                throw new IllegalStateException("Multiple classpath configuration resources /xq.yaml found");
            }
            try (InputStream input = resource.openStream()) {
                String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                Properties properties = new Properties();
                properties.load(new java.io.StringReader(source));
                return parseStubSettings(properties, source).withEnvironmentOverrides();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read classpath configuration /xq.yaml", exception);
        }
    }

    private static StubSettings parseStubSettings(Properties properties, String source) {
        String enabled = value(properties, source, "enabled", "false");
        String host = value(properties, source, "host", "127.0.0.1");
        String port = value(properties, source, "port", "0");
        String reset = value(properties, source, "reset-before-scenario", "true");
        String isolate = value(properties, source, "isolate-scenarios", "true");
        try {
            return new StubSettings(Boolean.parseBoolean(booleanText(enabled, "enabled")), host,
                    Integer.parseInt(port), Boolean.parseBoolean(booleanText(reset, "reset-before-scenario")),
                    Boolean.parseBoolean(booleanText(isolate, "isolate-scenarios")));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid stub port in /xq.yaml", exception);
        }
    }

    private static String value(Properties properties, String source, String name, String fallback) {
        String flat = properties.getProperty("xq.stub." + name);
        String nested = nestedValue(source, name);
        return nested != null ? nested : flat == null || flat.isBlank() ? fallback : flat.trim();
    }

    private static String nestedValue(String source, String name) {
        boolean inStub = false;
        for (String line : source.split("\\R")) {
            String withoutComment = line.split("#", 2)[0];
            if (withoutComment.isBlank()) continue;
            int indent = withoutComment.indexOf(withoutComment.stripLeading());
            String content = withoutComment.strip();
            if (indent == 0) {
                inStub = "stub:".equals(content);
                continue;
            }
            if (inStub && content.startsWith(name + ":")) {
                return unquote(content.substring(name.length() + 1).trim());
            }
        }
        return null;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String booleanText(String value, String name) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalStateException("Invalid stub " + name + " in /xq.yaml");
        }
        return value;
    }

    public record StubSettings(boolean enabled, String host, int port, boolean resetBeforeScenario,
                               boolean isolateScenarios) {
        public StubSettings {
            if (host == null || host.isBlank() || host.contains("/") || host.contains(":")) {
                throw new IllegalStateException("Invalid stub host in /xq.yaml");
            }
            if (port < 0 || port > 65535) {
                throw new IllegalStateException("Invalid stub port in /xq.yaml");
            }
        }

        private StubSettings withEnvironmentOverrides() {
            String envEnabled = System.getenv("XQ_STUB_ENABLED");
            String envHost = System.getenv("XQ_STUB_HOST");
            String envPort = System.getenv("XQ_STUB_PORT");
            String envReset = System.getenv("XQ_STUB_RESET_BEFORE_SCENARIO");
            String envIsolate = System.getenv("XQ_STUB_ISOLATE_SCENARIOS");
            try {
                return new StubSettings(
                        envEnabled == null || envEnabled.isBlank() ? enabled : Boolean.parseBoolean(booleanText(envEnabled, "enabled")),
                        envHost == null || envHost.isBlank() ? host : envHost.trim(),
                        envPort == null || envPort.isBlank() ? port : Integer.parseInt(envPort),
                        envReset == null || envReset.isBlank() ? resetBeforeScenario : Boolean.parseBoolean(booleanText(envReset, "reset-before-scenario")),
                        envIsolate == null || envIsolate.isBlank() ? isolateScenarios : Boolean.parseBoolean(booleanText(envIsolate, "isolate-scenarios")));
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("Invalid XQ_STUB_PORT environment variable", exception);
            }
        }
    }

    @Sources("classpath:xq.yaml")
    private interface Configuration extends Config {
        String testBaseUrl();
    }
}
