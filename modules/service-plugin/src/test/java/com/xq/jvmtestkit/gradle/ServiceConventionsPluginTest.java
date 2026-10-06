package com.xq.jvmtestkit.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
        assertTrue(result.getOutput().contains("cucumberE2eTest"));
        assertTrue(result.getOutput().contains("startE2eService"));
        assertTrue(result.getOutput().contains("stopE2eService"));
        assertTrue(result.getOutput().contains("bootBuildImage"));
    }

    @Test
    void exposesCucumberRunnerAsGradleTestTask() throws IOException {
        writeSettings();
        writeBuild("""
                import org.gradle.api.tasks.testing.Test

                plugins {
                    id 'com.xq.jvm-test-kit.service-plugin'
                }

                tasks.named('cucumberE2eTest', Test)
                """);
        writeMain(simpleMain());
        writeE2eCucumberFixture();

        BuildResult result = pluginClasspathRunner("cucumberE2eTest").build();

        assertEquals(SUCCESS, result.task(":cucumberE2eTest").getOutcome());
        Path testResults = projectDirectory.resolve("build/test-results/cucumberE2eTest");
        try (var results = Files.walk(testResults)) {
            assertTrue(results
                    .filter(path -> path.toString().endsWith(".xml"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .anyMatch(xml -> xml.contains("<testcase")));
        }
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
            Path ownershipFile = projectDirectory.resolve("build/application.pid.owner");
            assertTrue(Files.isRegularFile(ownershipFile));
            String ownership = Files.readString(ownershipFile);
            assertTrue(ownership.contains("formatVersion=1"));
            assertTrue(ownership.contains("pid="));
            assertTrue(ownership.contains("startedAt="));
            assertTrue(ownership.contains("serviceJar="));
            assertTrue(Files.isRegularFile(projectDirectory.resolve("build/application.log")));
        } finally {
            BuildResult stop = pluginClasspathRunner("stopE2eService").build();
            assertEquals(SUCCESS, stop.task(":stopE2eService").getOutcome());
        }
        assertFalse(Files.exists(projectDirectory.resolve("build/application.pid")));
        assertFalse(Files.exists(projectDirectory.resolve("build/application.pid.owner")));
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
        assertFalse(Files.exists(projectDirectory.resolve("build/application.pid.owner")));
        assertTrue(Files.isRegularFile(projectDirectory.resolve("build/application.log")));
    }

    @Test
    void timeoutTerminatesOnlyTheProcessItStartedAndRemovesOwnership() throws Exception {
        int servicePort = freePort();
        int healthPort = freePort();
        writeLifecycleProject(servicePort, healthPort, Duration.ofSeconds(1));

        BuildResult result = pluginClasspathRunner("startE2eService").buildAndFail();

        assertTrue(result.getOutput().contains("E2E service did not become healthy"));
        assertFalse(Files.exists(projectDirectory.resolve("build/application.pid")));
        assertFalse(Files.exists(projectDirectory.resolve("build/application.pid.owner")));
        assertTrue(Files.isRegularFile(projectDirectory.resolve("build/application.log")));
        awaitNoLiveServiceProcess();
    }

    @Test
    void ownershipWriteFailureTerminatesStartedProcessAndRemovesPid() throws Exception {
        int port = freePort();
        writeLifecycleProject(port, port, Duration.ofSeconds(20));
        Path ownershipFile = projectDirectory.resolve("build/application.pid.owner");
        Files.createDirectories(ownershipFile);

        BuildResult result = pluginClasspathRunner("startE2eService").buildAndFail();

        assertTrue(result.getOutput().contains("Could not start E2E service"));
        assertFalse(Files.exists(projectDirectory.resolve("build/application.pid")));
        assertFalse(Files.exists(ownershipFile));
        awaitNoLiveServiceProcess();
    }

    @Test
    void refusesToStopForeignLivePidAndPreservesRecord() throws IOException {
        writeSettings();
        writeBuild("""
                plugins {
                    id 'com.xq.jvm-test-kit.service-plugin'
                }
                """);
        writeMain(simpleMain());
        pluginClasspathRunner("packageService").build();
        Path pidFile = projectDirectory.resolve("build/application.pid");
        Files.writeString(pidFile, Long.toString(ProcessHandle.current().pid()));

        BuildResult result = pluginClasspathRunner("stopE2eService").buildAndFail();

        assertTrue(result.getOutput().contains("Refusing to stop PID"));
        assertTrue(ProcessHandle.current().isAlive());
        assertTrue(Files.isRegularFile(pidFile));
    }

    @Test
    void refusesSameJarProcessWithoutMatchingLaunchIdentity() throws Exception {
        int port = freePort();
        writeLifecycleProject(port, port, Duration.ofSeconds(20));
        pluginClasspathRunner("packageService").build();
        Path jar = projectDirectory.resolve("build/service/lifecycle-service.jar");
        Process impostor = new ProcessBuilder(javaExecutable(), "-jar", jar.toString())
                .redirectErrorStream(true)
                .redirectOutput(projectDirectory.resolve("build/impostor.log").toFile())
                .start();
        try {
            Path pidFile = projectDirectory.resolve("build/application.pid");
            Files.writeString(pidFile, Long.toString(impostor.pid()));

            BuildResult result = pluginClasspathRunner("stopE2eService").buildAndFail();

            assertTrue(result.getOutput().contains("Refusing to stop PID"));
            assertTrue(impostor.isAlive());
            assertTrue(Files.isRegularFile(pidFile));
        } finally {
            impostor.destroyForcibly();
            impostor.waitFor(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void rejectsMalformedPidWithoutDeletingIt() throws IOException {
        writeSettings();
        writeBuild("""
                plugins {
                    id 'com.xq.jvm-test-kit.service-plugin'
                }
                """);
        Path pidFile = projectDirectory.resolve("build/application.pid");
        Files.createDirectories(pidFile.getParent());
        Files.writeString(pidFile, "not-a-pid");

        BuildResult result = pluginClasspathRunner("stopE2eService").buildAndFail();

        assertTrue(result.getOutput().contains("Invalid service PID file"));
        assertEquals("not-a-pid", Files.readString(pidFile));
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
        assertFalse(Files.exists(projectDirectory.resolve("build/application.pid.owner")));
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
        URI repositoryUri = repositoryUri(repository);
        Files.writeString(projectDirectory.resolve("settings.gradle"), """
                pluginManagement {
                    repositories {
                        maven {
                            url = uri('%s')
                            def actor = providers.environmentVariable('GITHUB_ACTOR')
                            def token = providers.environmentVariable('GITHUB_TOKEN')
                            if (url.scheme in ['http', 'https'] && actor.isPresent() && token.isPresent()) {
                                credentials {
                                    username = actor.get()
                                    password = token.get()
                                }
                            }
                        }
                        gradlePluginPortal()
                        mavenCentral()
                    }
                }
                rootProject.name = 'published-consumer'
                """.formatted(repositoryUri));
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
        assertEquals(SUCCESS, result.task(":tasks").getOutcome());
        if ("file".equals(repositoryUri.getScheme())) {
            Path marker = Path.of(repositoryUri)
                    .resolve("com/xq/jvm-test-kit/service-plugin/com.xq.jvm-test-kit.service-plugin.gradle.plugin")
                    .resolve(version)
                    .resolve("com.xq.jvm-test-kit.service-plugin.gradle.plugin-" + version + ".pom");
            assertTrue(Files.isRegularFile(marker), marker.toString());
        }
    }

    private static URI repositoryUri(String repository) {
        URI candidate = URI.create(repository);
        return candidate.getScheme() == null ? Path.of(repository).toUri() : candidate;
    }

    private void writeLifecycleProject(int servicePort, int healthPort, Duration timeout) throws IOException {
        writeSettings();
        writeBuild("""
                import java.time.Duration

                plugins {
                    id 'com.xq.jvm-test-kit.service-plugin'
                }

                jvmTestKitService {
                    artifactName = 'lifecycle-service.jar'
                    healthUrl = 'http://127.0.0.1:%d/actuator/health'
                    startupTimeout = Duration.parse('%s')
                    environment = [SERVICE_PORT: '%d']
                }
                """.formatted(healthPort, timeout, servicePort));
        writeMain(healthServerMain());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private void awaitNoLiveServiceProcess() throws InterruptedException {
        Path jar = projectDirectory.resolve("build/service/lifecycle-service.jar");
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            Optional<ProcessHandle> process = ProcessHandle.allProcesses()
                    .filter(handle -> ownsJar(handle, jar))
                    .findFirst();
            if (process.isEmpty()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Lifecycle fixture process remained alive for " + jar);
    }

    private static boolean ownsJar(ProcessHandle handle, Path expectedJar) {
        Optional<String[]> arguments = handle.info().arguments();
        if (arguments.isEmpty()) {
            return false;
        }
        String[] values = arguments.get();
        for (int index = 0; index < values.length - 1; index++) {
            if ("-jar".equals(values[index])) {
                try {
                    Path actualJar = Path.of(values[index + 1]).toAbsolutePath().normalize();
                    return actualJar.equals(expectedJar.toAbsolutePath().normalize());
                } catch (RuntimeException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
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

    private void writeE2eCucumberFixture() throws IOException {
        Path stepDirectory = projectDirectory.resolve("src/e2e/java/example");
        Files.createDirectories(stepDirectory);
        Files.writeString(stepDirectory.resolve("SmokeSteps.java"), """
                package example;

                import io.cucumber.java.en.Given;

                public final class SmokeSteps {
                    @Given("a passing step")
                    public void passingStep() {
                    }
                }
                """);

        Files.writeString(stepDirectory.resolve("SpringConfiguration.java"), """
                package example;

                import io.cucumber.spring.CucumberContextConfiguration;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.test.context.ContextConfiguration;

                @CucumberContextConfiguration
                @ContextConfiguration(classes = SpringConfiguration.TestConfig.class)
                public class SpringConfiguration {
                    @Configuration
                    static class TestConfig {
                    }
                }
                """);

        Path resourceDirectory = projectDirectory.resolve("src/e2e/resources");
        Files.createDirectories(resourceDirectory.resolve("features"));
        Files.writeString(resourceDirectory.resolve("junit-platform.properties"),
                "cucumber.glue=example\ncucumber.features=classpath:features\n");
        Files.writeString(resourceDirectory.resolve("features/smoke.feature"), """
                Feature: smoke

                  Scenario: passing
                    Given a passing step
                """);
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
