package com.xq.jvmtestkit.config;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Config.DefaultValue;
import org.aeonbits.owner.Config.Key;

import static org.aeonbits.owner.Config.Sources;

/** Loads the test-kit configuration from the classpath. */
public final class ConfigurationManager {
    private static final Configuration configuration = ConfigFactory.create(Configuration.class);

    private ConfigurationManager() {}

    public static String getTestBaseUrl() {
        return configuration.testBaseUrl();
    }

    public static boolean isStubEnabled() {
        return StubConfiguration.load(Thread.currentThread().getContextClassLoader()).enabled();
    }

    public static String getStubHost() {
        return StubConfiguration.load(Thread.currentThread().getContextClassLoader()).host();
    }

    public static int getStubPort() {
        return StubConfiguration.load(Thread.currentThread().getContextClassLoader()).port();
    }

    public static boolean resetStubBeforeScenario() {
        return StubConfiguration.load(Thread.currentThread().getContextClassLoader()).resetBeforeScenario();
    }

    public static boolean isolateStubScenarios() {
        return StubConfiguration.load(Thread.currentThread().getContextClassLoader()).isolateScenarios();
    }

    @Sources("classpath:xq.yaml")
    private interface Configuration extends Config {
        String testBaseUrl();

        @Key("xq.stub.enabled")
        @DefaultValue("false")
        boolean stubEnabled();

        @Key("xq.stub.host")
        @DefaultValue("127.0.0.1")
        String stubHost();

        @Key("xq.stub.port")
        @DefaultValue("0")
        int stubPort();

        @Key("xq.stub.reset-before-scenario")
        @DefaultValue("true")
        boolean resetStubBeforeScenario();

        @Key("xq.stub.isolate-scenarios")
        @DefaultValue("true")
        boolean isolateStubScenarios();
    }

    private static String stringOverride(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static boolean booleanOverride(String name, boolean fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) return fallback;
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalStateException("Invalid boolean environment variable " + name);
        }
        return Boolean.parseBoolean(value);
    }

    private static int integerOverride(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid integer environment variable " + name, exception);
        }
    }
}
