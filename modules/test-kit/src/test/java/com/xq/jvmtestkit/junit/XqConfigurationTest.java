package com.xq.jvmtestkit.junit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XqConfigurationTest {
    @Test
    void loadsAndNormalizesRequiredRestUri() throws Exception {
        Path root = Files.createTempDirectory("xq-config-");
        Files.writeString(root.resolve("xq.yaml"),
                "xq.rest.base-uri: https://example.test/api\n");

        try (URLClassLoader loader = new URLClassLoader(new URL[]{root.toUri().toURL()}, null)) {
            assertEquals("https://example.test/api/", XqConfiguration.load(loader).restBaseUri().toString());
        }
    }

    @Test
    void loadsOptionalStubConfiguration() throws Exception {
        Path root = Files.createTempDirectory("xq-stub-config-");
        Files.writeString(root.resolve("xq.yaml"), """
                xq.rest.base-uri: http://example.test/api
                stub:
                  enabled: true
                  host: 127.0.0.1
                  port: 18089
                  reset-before-scenario: false
                  isolate-scenarios: false
                """);
        try (URLClassLoader loader = new URLClassLoader(new URL[]{root.toUri().toURL()}, null)) {
            XqConfiguration configuration = XqConfiguration.load(loader);
            assertEquals(true, configuration.stub().enabled());
            assertEquals("127.0.0.1", configuration.stub().host());
            assertEquals(18089, configuration.stub().port());
            assertEquals(false, configuration.stub().resetBeforeScenario());
            assertEquals(false, configuration.stub().isolateScenarios());
        }
    }

    @Test
    void rejectsMissingDuplicateAndUnsafeConfiguration() throws Exception {
        Path empty = Files.createTempDirectory("xq-empty-");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{empty.toUri().toURL()}, null)) {
            assertThrows(IllegalStateException.class, () -> XqConfiguration.load(loader));
        }

        Path first = Files.createTempDirectory("xq-first-");
        Path second = Files.createTempDirectory("xq-second-");
        Files.writeString(first.resolve("xq.yaml"), "xq.rest.base-uri: http://one.test/\n");
        Files.writeString(second.resolve("xq.yaml"), "xq.rest.base-uri: http://two.test/\n");
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{first.toUri().toURL(), second.toUri().toURL()}, null)) {
            assertThrows(IllegalStateException.class, () -> XqConfiguration.load(loader));
        }

        Path unsafe = Files.createTempDirectory("xq-unsafe-");
        Files.writeString(unsafe.resolve("xq.yaml"),
                "xq.rest.base-uri: https://user:secret@example.test/?token=secret\n");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{unsafe.toUri().toURL()}, null)) {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> XqConfiguration.load(loader));
            assertEquals(false, failure.getMessage().contains("secret"));
        }
    }
}
