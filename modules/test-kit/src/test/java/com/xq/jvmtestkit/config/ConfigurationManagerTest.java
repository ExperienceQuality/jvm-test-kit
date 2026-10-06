package com.xq.jvmtestkit.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigurationManagerTest {
    @Test
    void loadsTestBaseUrlFromClasspathConfiguration() {
        assertEquals("http://localhost:8080", ConfigurationManager.getTestBaseUrl());
    }
}
