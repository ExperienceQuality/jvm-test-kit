package com.xq.jvmtestkit.gradle.internal.task;

import com.xq.jvmtestkit.gradle.internal.ServiceConfiguration;
import com.xq.jvmtestkit.gradle.internal.lifecycle.ServiceProcessSupport;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;

@DisableCachingByDefault(because = "Starts an external service process")
public abstract class StartE2eServiceTask extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getServiceJar();

    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract RegularFileProperty getJavaExecutable();

    @Input
    public abstract Property<String> getHealthUrl();

    @Input
    public abstract Property<Duration> getStartupTimeout();

    @Input
    public abstract ListProperty<String> getJvmArgs();

    @Internal
    public abstract MapProperty<String, String> getEnvironment();

    @Internal
    public abstract RegularFileProperty getPidFile();

    @Internal
    public abstract RegularFileProperty getLogFile();

    @TaskAction
    public void startService() {
        Path jar = getServiceJar().get().getAsFile().toPath().toAbsolutePath().normalize();
        URI healthUri = ServiceConfiguration.validateHealthUrl(getHealthUrl().get());
        Duration timeout = ServiceConfiguration.validateStartupTimeout(getStartupTimeout().get());
        Path pidFile = getPidFile().get().getAsFile().toPath();
        Path ownershipFile = ServiceProcessSupport.ownershipFile(pidFile);
        Path logFile = getLogFile().get().getAsFile().toPath();

        ServiceProcessSupport.readPid(pidFile).ifPresent(pid -> {
            OptionalProcess existing = OptionalProcess.from(pid);
            if (existing.handle() != null && existing.handle().isAlive()) {
                if (ServiceProcessSupport.ownsLaunch(existing.handle(), jar, ownershipFile)) {
                    throw new GradleException("E2E service is already running with PID " + pid + " from " + jar);
                }
                throw new GradleException("Refusing to replace PID file owned by another live process: " + pidFile);
            }
            ServiceProcessSupport.deleteState(pidFile);
        });

        try {
            Files.createDirectories(pidFile.getParent());
            Files.createDirectories(logFile.getParent());
        } catch (IOException exception) {
            throw new GradleException("Could not create E2E service output directory", exception);
        }

        var command = new ArrayList<String>();
        command.add(getJavaExecutable().get().getAsFile().getAbsolutePath());
        command.addAll(getJvmArgs().get());
        command.add("-jar");
        command.add(jar.toString());

        Process process = null;
        try {
            var builder = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()));
            builder.environment().putAll(getEnvironment().get());
            process = builder.start();
            Files.writeString(pidFile, Long.toString(process.pid()) + System.lineSeparator(), StandardCharsets.UTF_8);
            ServiceProcessSupport.writeOwnership(ownershipFile, process.toHandle(), jar);
        } catch (IOException exception) {
            GradleException failure = new GradleException("Could not start E2E service; see " + logFile, exception);
            cleanupStartedProcess(process, pidFile, failure);
            throw failure;
        }

        boolean healthy = false;
        Throwable failure = null;
        try {
            waitUntilHealthy(process, healthUri, timeout, logFile);
            healthy = true;
            getLogger().lifecycle("E2E service is healthy at {} (PID {})", healthUri, process.pid());
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            if (!healthy) {
                cleanupStartedProcess(process, pidFile, failure);
            }
        }
    }

    private void waitUntilHealthy(Process process, URI healthUri, Duration timeout, Path logFile) {
        long deadline = System.nanoTime() + timeout.toNanos();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            while (System.nanoTime() < deadline) {
                if (!process.isAlive()) {
                    int exitCode = process.exitValue();
                    throw new GradleException(
                            "E2E service exited with code " + exitCode + " before becoming healthy; see " + logFile
                    );
                }
                if (isHealthy(client, healthUri)) {
                    return;
                }
                try {
                    Thread.sleep(250);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new GradleException(
                            "Interrupted while waiting for E2E service health; see " + logFile,
                            exception
                    );
                }
            }
        }
        throw new GradleException(
                "E2E service did not become healthy at " + healthUri + " within " + timeout + "; see " + logFile
        );
    }

    private boolean isHealthy(HttpClient client, URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status >= 200 && status < 300;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GradleException("Interrupted while checking E2E service health", exception);
        }
    }

    private static void cleanupStartedProcess(Process process, Path pidFile, Throwable originalFailure) {
        boolean interrupted = Thread.interrupted();
        RuntimeException cleanupFailure = null;
        boolean processStopped = process == null;
        if (process != null) {
            try {
                ServiceProcessSupport.terminate(process.toHandle(), Duration.ofSeconds(5));
            } catch (RuntimeException exception) {
                cleanupFailure = exception;
            }
            processStopped = !process.isAlive();
        }
        if (processStopped) {
            try {
                ServiceProcessSupport.deleteState(pidFile);
            } catch (RuntimeException exception) {
                if (cleanupFailure == null) {
                    cleanupFailure = exception;
                } else {
                    cleanupFailure.addSuppressed(exception);
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        if (cleanupFailure != null) {
            if (originalFailure != null) {
                originalFailure.addSuppressed(cleanupFailure);
            } else {
                throw cleanupFailure;
            }
        }
    }

    private record OptionalProcess(ProcessHandle handle) {
        static OptionalProcess from(long pid) {
            return new OptionalProcess(ProcessHandle.of(pid).orElse(null));
        }
    }
}
