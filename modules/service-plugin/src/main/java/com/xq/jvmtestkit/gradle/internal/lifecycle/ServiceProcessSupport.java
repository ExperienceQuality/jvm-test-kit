package com.xq.jvmtestkit.gradle.internal.lifecycle;

import org.gradle.api.GradleException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class ServiceProcessSupport {
    private ServiceProcessSupport() {
    }

    public static Optional<Long> readPid(Path pidFile) {
        if (!Files.exists(pidFile)) {
            return Optional.empty();
        }
        try {
            String value = Files.readString(pidFile, StandardCharsets.UTF_8).trim();
            long pid = Long.parseLong(value);
            if (pid <= 0) {
                throw new NumberFormatException("PID must be positive");
            }
            return Optional.of(pid);
        } catch (IOException | NumberFormatException exception) {
            throw new GradleException("Invalid service PID file " + pidFile + "; remove it only after confirming no service process owns it", exception);
        }
    }

    static boolean ownsJar(ProcessHandle handle, Path expectedJar) {
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

    public static Path ownershipFile(Path pidFile) {
        return pidFile.resolveSibling(pidFile.getFileName() + ".owner");
    }

    public static void writeOwnership(Path ownershipFile, ProcessHandle handle, Path serviceJar) throws IOException {
        OwnedProcessMetadata.capture(handle, serviceJar).write(ownershipFile);
    }

    public static boolean ownsLaunch(ProcessHandle handle, Path expectedJar, Path ownershipFile) {
        if (!handle.isAlive() || !ownsJar(handle, expectedJar)) {
            return false;
        }
        return OwnedProcessMetadata.read(ownershipFile)
                .filter(metadata -> metadata.matches(handle, expectedJar))
                .isPresent();
    }

    public static void deleteState(Path pidFile) {
        deleteFile(ownershipFile(pidFile), "service ownership file");
        deleteFile(pidFile, "service PID file");
    }

    private static void deleteFile(Path path, String label) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new GradleException("Could not remove " + label + " " + path, exception);
        }
    }

    public static void terminate(ProcessHandle handle, Duration gracefulTimeout) {
        if (!handle.isAlive()) {
            return;
        }
        handle.destroy();
        if (awaitExit(handle, gracefulTimeout)) {
            return;
        }
        handle.destroyForcibly();
        if (!awaitExit(handle, Duration.ofSeconds(5))) {
            throw new GradleException("Service process " + handle.pid() + " did not stop after forced termination");
        }
    }

    private static boolean awaitExit(ProcessHandle handle, Duration timeout) {
        try {
            handle.onExit().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (java.util.concurrent.TimeoutException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GradleException("Interrupted while stopping service process " + handle.pid(), exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new GradleException("Could not observe service process " + handle.pid() + " termination", exception);
        }
    }
}
