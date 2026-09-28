package com.xq.jvmtestkit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

@DisableCachingByDefault(because = 'Starts an external service process')
abstract class StartE2eServiceTask extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getServiceJar()

    @InputFile
    @PathSensitive(PathSensitivity.ABSOLUTE)
    abstract RegularFileProperty getJavaExecutable()

    @Input
    abstract Property<String> getHealthUrl()

    @Input
    abstract Property<Duration> getStartupTimeout()

    @Input
    abstract ListProperty<String> getJvmArgs()

    @Internal
    abstract MapProperty<String, String> getEnvironment()

    @Internal
    abstract RegularFileProperty getPidFile()

    @Internal
    abstract RegularFileProperty getLogFile()

    @TaskAction
    void startService() {
        Path jar = serviceJar.get().asFile.toPath().toAbsolutePath().normalize()
        URI healthUri = ServiceConfiguration.validateHealthUrl(healthUrl.get())
        Duration timeout = ServiceConfiguration.validateStartupTimeout(startupTimeout.get())
        Path pid = pidFile.get().asFile.toPath()
        Path log = logFile.get().asFile.toPath()

        Optional<Long> recordedPid = ServiceProcessSupport.readPid(pid)
        if (recordedPid.present) {
            long value = recordedPid.get()
            Optional<ProcessHandle> existing = ProcessHandle.of(value)
            if (existing.present && existing.get().alive) {
                if (ServiceProcessSupport.ownsJar(existing.get(), jar)) {
                    throw new GradleException("E2E service is already running with PID ${value} from ${jar}")
                }
                throw new GradleException("Refusing to replace PID file owned by another live process: ${pid}")
            }
            deletePid(pid)
        }

        try {
            Files.createDirectories(pid.parent)
            Files.createDirectories(log.parent)
        } catch (IOException exception) {
            throw new GradleException('Could not create E2E service output directory', exception)
        }

        List<String> command = new ArrayList<>()
        command.add(javaExecutable.get().asFile.absolutePath)
        command.addAll(jvmArgs.get())
        command.add('-jar')
        command.add(jar.toString())

        Process process
        try {
            ProcessBuilder builder = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.to(log.toFile()))
            builder.environment().putAll(environment.get())
            process = builder.start()
            Files.writeString(pid, Long.toString(process.pid()) + System.lineSeparator(), StandardCharsets.UTF_8)
        } catch (IOException exception) {
            deletePid(pid)
            throw new GradleException("Could not start E2E service; see ${log}", exception)
        }

        long deadline = System.nanoTime() + timeout.toNanos()
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
        try {
            while (System.nanoTime() < deadline) {
                if (!process.alive) {
                    int exitCode = process.exitValue()
                    deletePid(pid)
                    throw new GradleException("E2E service exited with code ${exitCode} before becoming healthy; see ${log}")
                }
                if (isHealthy(client, healthUri)) {
                    logger.lifecycle('E2E service is healthy at {} (PID {})', healthUri, process.pid())
                    return
                }
                Thread.sleep(250)
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt()
            ServiceProcessSupport.terminate(process.toHandle(), Duration.ofSeconds(5))
            deletePid(pid)
            throw new GradleException("Interrupted while waiting for E2E service health; see ${log}", exception)
        } finally {
            client.close()
        }

        ServiceProcessSupport.terminate(process.toHandle(), Duration.ofSeconds(5))
        deletePid(pid)
        throw new GradleException("E2E service did not become healthy at ${healthUri} within ${timeout}; see ${log}")
    }

    private static boolean isHealthy(HttpClient client, URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build()
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
            return status >= 200 && status < 300
        } catch (IOException ignored) {
            return false
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt()
            throw new GradleException('Interrupted while checking E2E service health', exception)
        }
    }

    private static void deletePid(Path pid) {
        try {
            Files.deleteIfExists(pid)
        } catch (IOException exception) {
            throw new GradleException("Could not remove service PID file ${pid}", exception)
        }
    }
}
