package com.xq.jvmtestkit.gradle.internal.lifecycle;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceProcessSupportTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void terminationInterruptionRestoresInterruptFlag() {
        Thread.currentThread().interrupt();
        try {
            GradleException failure = assertThrows(
                    GradleException.class,
                    () -> ServiceProcessSupport.terminate(new NeverExitsProcessHandle(), Duration.ofSeconds(1))
            );

            assertTrue(failure.getMessage().contains("Interrupted while stopping service process"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void rejectsReusedPidWhenProcessStartIdentityChanged() throws Exception {
        Path jar = temporaryDirectory.resolve("service.jar").toAbsolutePath();
        Path ownershipFile = temporaryDirectory.resolve("application.pid.owner");
        ProcessHandle original = new IdentifiedProcessHandle(
                434343L,
                jar,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        ServiceProcessSupport.writeOwnership(ownershipFile, original, jar);
        ProcessHandle handle = new IdentifiedProcessHandle(
                434343L,
                jar,
                Instant.parse("2026-01-01T00:00:01Z")
        );

        assertFalse(ServiceProcessSupport.ownsLaunch(handle, jar, ownershipFile));
    }

    @Test
    void ownershipRequiresPidStartTimeJarAndCommandAgreement() throws Exception {
        Path jar = temporaryDirectory.resolve("service.jar").toAbsolutePath();
        Path otherJar = temporaryDirectory.resolve("other.jar").toAbsolutePath();
        Instant startedAt = Instant.parse("2026-01-01T00:00:00Z");
        Path ownershipFile = temporaryDirectory.resolve("application.pid.owner");
        ProcessHandle owner = new IdentifiedProcessHandle(434343L, jar, startedAt);
        ServiceProcessSupport.writeOwnership(ownershipFile, owner, jar);

        assertTrue(ServiceProcessSupport.ownsLaunch(owner, jar, ownershipFile));
        assertFalse(ServiceProcessSupport.ownsLaunch(
                new IdentifiedProcessHandle(434344L, jar, startedAt),
                jar,
                ownershipFile
        ));
        assertFalse(ServiceProcessSupport.ownsLaunch(
                new IdentifiedProcessHandle(434343L, otherJar, startedAt),
                jar,
                ownershipFile
        ));
        assertFalse(ServiceProcessSupport.ownsLaunch(owner, otherJar, ownershipFile));
    }

    @Test
    void malformedOwnershipMetadataFailsClosed() throws Exception {
        Path jar = temporaryDirectory.resolve("service.jar").toAbsolutePath();
        Path ownershipFile = temporaryDirectory.resolve("application.pid.owner");
        Files.writeString(ownershipFile, "formatVersion=1\npid=not-a-number\n");
        ProcessHandle owner = new IdentifiedProcessHandle(
                434343L,
                jar,
                Instant.parse("2026-01-01T00:00:00Z")
        );

        assertFalse(ServiceProcessSupport.ownsLaunch(owner, jar, ownershipFile));
        assertEquals("formatVersion=1\npid=not-a-number\n", Files.readString(ownershipFile));
    }

    private static final class NeverExitsProcessHandle implements ProcessHandle {
        private final CompletableFuture<ProcessHandle> exit = new CompletableFuture<>();

        @Override public long pid() { return 424242L; }
        @Override public Optional<ProcessHandle> parent() { return Optional.empty(); }
        @Override public Stream<ProcessHandle> children() { return Stream.empty(); }
        @Override public Stream<ProcessHandle> descendants() { return Stream.empty(); }
        @Override public Info info() { throw new UnsupportedOperationException(); }
        @Override public CompletableFuture<ProcessHandle> onExit() { return exit; }
        @Override public boolean supportsNormalTermination() { return true; }
        @Override public boolean destroy() { return true; }
        @Override public boolean destroyForcibly() { return true; }
        @Override public boolean isAlive() { return true; }
        @Override public int compareTo(ProcessHandle other) { return Long.compare(pid(), other.pid()); }
    }

    private static final class IdentifiedProcessHandle implements ProcessHandle {
        private final long pid;
        private final Path jar;
        private final Instant startedAt;

        private IdentifiedProcessHandle(long pid, Path jar, Instant startedAt) {
            this.pid = pid;
            this.jar = jar;
            this.startedAt = startedAt;
        }

        @Override public long pid() { return pid; }
        @Override public Optional<ProcessHandle> parent() { return Optional.empty(); }
        @Override public Stream<ProcessHandle> children() { return Stream.empty(); }
        @Override public Stream<ProcessHandle> descendants() { return Stream.empty(); }
        @Override public Info info() {
            return new Info() {
                @Override public Optional<String> command() { return Optional.of("java"); }
                @Override public Optional<String> commandLine() { return Optional.empty(); }
                @Override public Optional<String[]> arguments() {
                    return Optional.of(new String[]{"-jar", jar.toString()});
                }
                @Override public Optional<Instant> startInstant() { return Optional.of(startedAt); }
                @Override public Optional<Duration> totalCpuDuration() { return Optional.empty(); }
                @Override public Optional<String> user() { return Optional.empty(); }
            };
        }
        @Override public CompletableFuture<ProcessHandle> onExit() { return new CompletableFuture<>(); }
        @Override public boolean supportsNormalTermination() { return true; }
        @Override public boolean destroy() { return true; }
        @Override public boolean destroyForcibly() { return true; }
        @Override public boolean isAlive() { return true; }
        @Override public int compareTo(ProcessHandle other) { return Long.compare(pid(), other.pid()); }
    }
}
