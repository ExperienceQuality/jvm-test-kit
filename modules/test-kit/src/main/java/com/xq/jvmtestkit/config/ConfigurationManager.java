package com.xq.jvmtestkit.config;

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

    @Sources("classpath:xq.yaml")
    private interface Configuration extends Config {
        String testBaseUrl();
    }
}
