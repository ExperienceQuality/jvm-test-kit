package com.xq.jvmtestkit.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceConventionsPluginTest {
    @TempDir
    Path projectDirectory;

    @Test
    void packagesARepresentativeGradle8145Consumer() throws IOException {
        writeSettings();
        writeBuild("""
                plugins {
                    id 'com.xq.jvm-test-kit.service-plugin'
                }

                jvmTestKitService {
                    artifactName = 'sample-service.jar'
                }
                """);
        writeMain(simpleMain());

        BuildResult result = pluginClasspathRunner("packageService", "tasks", "--all").build();

        assertEquals(SUCCESS, result.task(":packageService").getOutcome());
        assertTrue(Files.isRegularFile(projectDirectory.resolve("build/service/sample-service.jar")));
        assertTrue(result.getOutput().contains("e2eTest"));
        assertTrue(result.getOutput().contains("startE2eService"));
        assertTrue(result.getOutput().contains("stopE2eService"));
        assertTrue(result.getOutput().contains("bootBuildImage"));
    }

    @Test
    void rejectsUnsafeArtifactName() throws IOException {
        writeSettings();
        writeBuild("""
                plugins {
                    id 'com.xq.jvm-test-kit.service-plugin'
                }

                jvmTestKitService {
                    artifactName = '../unsafe.jar'
                }
                """);
        writeMain(simpleMain());

        BuildResult result = pluginClasspathRunner("packageService").buildAndFail();

        assertTrue(result.getOutput().contains(
                "jvmTestKitService.artifactName must be a JAR basename without path segments, for example service.jar"
        ));
        assertFalse(Files.exists(projectDirectory.resolve("unsafe.jar")));
    }

    @Test
    void startsAndStopsOnlyItsPackagedService() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        writeSettings();
        writeBuild("""
                import java.time.Duration

                plugins {
                    id 'com.xq.jvm-test-kit.service-plugin'
                }

                jvmTestKitService {
                    artifactName = 'lifecycle-service.jar'
                    healthUrl = 'http://127.0.0.1:%d/actuator/health'
                    startupTimeout = Duration.ofSeconds(20)
                    environment = [SERVICE_PORT: '%d']
                }
                """.formatted(port, port));
        writeMain(healthServerMain());

        try {
            BuildResult start = pluginClasspathRunner("startE2eService").build();
            assertEquals(SUCCESS, start.task(":startE2eService").getOutcome());
            assertTrue(Files.isRegularFile(projectDirectory.resolve("build/application.pid")));
            assertTrue(Files.isRegularFile(projectDirectory.resolve("build/application.log")));
        } finally {
            BuildResult stop = pluginClasspathRunner("stopE2eService").build();
            assertEquals(SUCCESS, stop.task(":stopE2eService").getOutcome());
        }
        assertFalse(Files.exists(projectDirectory.resolve("build/application.pid")));
    }

    @Test
    void earlyServiceExitFailsWithoutLeavingPid() throws IOException {
        writeSettings();
        writeBuild("""
                import java.time.Duration

                plugins {
                    id 'com.xq.jvm-test-kit.service-plugin'
                }

                jvmTestKitService {
                    healthUrl = 'http://127.0.0.1:1/actuator/health'
                    startupTimeout = Duration.ofSeconds(5)
                }
                """);
        writeMain(simpleMain());

        BuildResult result = pluginClasspathRunner("startE2eService").buildAndFail();

        assertTrue(result.getOutput().contains("E2E service exited with code 0 before becoming healthy"));
        assertFalse(Files.exists(projectDirectory.resolve("build/application.pid")));
        assertTrue(Files.isRegularFile(projectDirectory.resolve("build/application.log")));
    }

    @Test
    void removesDeadPidIdempotently() throws IOException {
        writeSettings();
        writeBuild("""
                plugins {
                    id 'com.xq.jvm-test-kit.service-plugin'
                }
                """);
        Path pidFile = projectDirectory.resolve("build/application.pid");
        Files.createDirectories(pidFile.getParent());
        Files.writeString(pidFile, Long.toString(Long.MAX_VALUE));

        BuildResult result = pluginClasspathRunner("stopE2eService").build();

        assertEquals(SUCCESS, result.task(":stopE2eService").getOutcome());
        assertFalse(Files.exists(pidFile));
    }

    @Test
    void reusesConfigurationCache() throws IOException {
        writeSettings();
        writeBuild("""
                plugins {
                    id 'com.xq.jvm-test-kit.service-plugin'
                }
                """);
        writeMain(simpleMain());

        pluginClasspathRunner("packageService", "--configuration-cache").build();
        pluginClasspathRunner("packageService", "--configuration-cache").build();
        BuildResult third = pluginClasspathRunner("packageService", "--configuration-cache").build();

        assertTrue(
                third.getOutput().contains("Reusing configuration cache.")
                        || third.getOutput().contains("Configuration cache entry reused."),
                third.getOutput()
        );
    }

    @Test
    void resolvesGeneratedMarkerAtExactVersion() throws IOException {
        String repository = System.getProperty("xq.plugin.testRepository");
        String version = System.getProperty("xq.plugin.version");
        Files.writeString(projectDirectory.resolve("settings.gradle"), """
                pluginManagement {
                    repositories {
                        maven { url = uri('%s') }
                        gradlePluginPortal()
                        mavenCentral()
                    }
                }
                rootProject.name = 'published-consumer'
                """.formatted(Path.of(repository).toUri()));
        writeBuild("""
                plugins {
                    id 'com.xq.jvm-test-kit.service-plugin' version '%s'
                }
                """.formatted(version));

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withGradleVersion("8.14.5")
                .withArguments("--stacktrace", "tasks", "--all")
                .build();

        assertTrue(result.getOutput().contains("packageService"));
        try (var paths = Files.walk(Path.of(repository))) {
            assertTrue(paths.anyMatch(path -> path.getFileName().toString().contains("service-plugin.gradle.plugin")));
        }
    }

    private GradleRunner pluginClasspathRunner(String... arguments) {
        String[] fullArguments = new String[arguments.length + 1];
        fullArguments[0] = "--stacktrace";
        System.arraycopy(arguments, 0, fullArguments, 1, arguments.length);
        return GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withGradleVersion("8.14.5")
                .withPluginClasspath()
                .withArguments(fullArguments);
    }

    private void writeSettings() throws IOException {
        Files.writeString(projectDirectory.resolve("settings.gradle"), "rootProject.name = 'sample-service'\n");
    }

    private void writeBuild(String build) throws IOException {
        Files.writeString(projectDirectory.resolve("build.gradle"), build);
    }

    private void writeMain(String source) throws IOException {
        Path sourceDirectory = projectDirectory.resolve("src/main/java/example");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("Application.java"), source);
    }

    private static String simpleMain() {
        return """
                package example;

                public final class Application {
                    public static void main(String[] args) {
                        System.out.println("sample service");
                    }
                }
                """;
    }

    private static String healthServerMain() {
        return """
                package example;

                import java.io.InputStream;
                import java.io.OutputStream;
                import java.net.ServerSocket;
                import java.net.Socket;
                import java.nio.charset.StandardCharsets;

                public final class Application {
                    public static void main(String[] args) throws Exception {
                        int port = Integer.parseInt(System.getenv("SERVICE_PORT"));
                        try (ServerSocket server = new ServerSocket(port)) {
                            while (true) {
                                try (Socket socket = server.accept()) {
                                    InputStream input = socket.getInputStream();
                                    while (true) {
                                        int value = input.read();
                                        if (value < 0) break;
                                        if (value == '\\n' && input.available() == 0) break;
                                    }
                                    byte[] body = "{\\"status\\":\\"UP\\"}".getBytes(StandardCharsets.UTF_8);
                                    byte[] headers = ("HTTP/1.1 200 OK\\r\\nContent-Type: application/json\\r\\nContent-Length: "
                                            + body.length + "\\r\\nConnection: close\\r\\n\\r\\n").getBytes(StandardCharsets.UTF_8);
                                    OutputStream output = socket.getOutputStream();
                                    output.write(headers);
                                    output.write(body);
                                    output.flush();
                                }
                            }
                        }
                    }
                }
                """;
    }
}
