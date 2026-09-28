package com.xq.jvmtestkit.gradle

import org.gradle.api.GradleException

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

final class ServiceProcessSupport {
    private ServiceProcessSupport() {
    }

    static Optional<Long> readPid(Path pidFile) {
        if (!Files.exists(pidFile)) {
            return Optional.empty()
        }
        try {
            String value = Files.readString(pidFile, StandardCharsets.UTF_8).trim()
            long pid = Long.parseLong(value)
            if (pid <= 0) {
                throw new NumberFormatException('PID must be positive')
            }
            return Optional.of(pid)
        } catch (IOException | NumberFormatException exception) {
            throw new GradleException("Invalid service PID file ${pidFile}; remove it only after confirming no service process owns it", exception)
        }
    }

    static boolean ownsJar(ProcessHandle handle, Path expectedJar) {
        Optional<String[]> arguments = handle.info().arguments()
        if (arguments.empty) {
            return false
        }
        String[] values = arguments.get()
        for (int index = 0; index < values.length - 1; index++) {
            if (values[index] == '-jar') {
                try {
                    Path actualJar = Path.of(values[index + 1]).toAbsolutePath().normalize()
                    return actualJar == expectedJar.toAbsolutePath().normalize()
                } catch (RuntimeException ignored) {
                    return false
                }
            }
        }
        false
    }

    static void terminate(ProcessHandle handle, Duration gracefulTimeout) {
        if (!handle.alive) {
            return
        }
        handle.destroy()
        if (awaitExit(handle, gracefulTimeout)) {
            return
        }
        handle.destroyForcibly()
        if (!awaitExit(handle, Duration.ofSeconds(5))) {
            throw new GradleException("Service process ${handle.pid()} did not stop after forced termination")
        }
    }

    private static boolean awaitExit(ProcessHandle handle, Duration timeout) {
        try {
            handle.onExit().get(timeout.toMillis(), TimeUnit.MILLISECONDS)
            return true
        } catch (TimeoutException ignored) {
            return false
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt()
            throw new GradleException("Interrupted while stopping service process ${handle.pid()}", exception)
        } catch (ExecutionException exception) {
            throw new GradleException("Could not observe service process ${handle.pid()} termination", exception)
        }
    }
}
