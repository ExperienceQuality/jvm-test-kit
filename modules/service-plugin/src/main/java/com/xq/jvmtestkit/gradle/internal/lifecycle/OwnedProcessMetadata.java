package com.xq.jvmtestkit.gradle.internal.lifecycle;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

record OwnedProcessMetadata(long pid, Instant startedAt, Path serviceJar) {
    private static final String FORMAT_VERSION = "1";

    OwnedProcessMetadata {
        if (pid <= 0) {
            throw new IllegalArgumentException("PID must be positive");
        }
        serviceJar = normalize(serviceJar);
    }

    static OwnedProcessMetadata capture(ProcessHandle handle, Path serviceJar) throws IOException {
        Instant startedAt = handle.info().startInstant()
                .orElseThrow(() -> new IOException("Process start time is unavailable for PID " + handle.pid()));
        return new OwnedProcessMetadata(handle.pid(), startedAt, serviceJar);
    }

    void write(Path ownershipFile) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("formatVersion", FORMAT_VERSION);
        properties.setProperty("pid", Long.toString(pid));
        properties.setProperty("startedAt", startedAt.toString());
        properties.setProperty("serviceJar", serviceJar.toString());
        try (Writer writer = Files.newBufferedWriter(
                ownershipFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        )) {
            properties.store(writer, "XQ-owned service process");
        }
    }

    static Optional<OwnedProcessMetadata> read(Path ownershipFile) {
        if (!Files.isRegularFile(ownershipFile)) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(ownershipFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
            if (!FORMAT_VERSION.equals(properties.getProperty("formatVersion"))) {
                return Optional.empty();
            }
            long pid = Long.parseLong(required(properties, "pid"));
            Instant startedAt = Instant.parse(required(properties, "startedAt"));
            Path serviceJar = Path.of(required(properties, "serviceJar"));
            return Optional.of(new OwnedProcessMetadata(pid, startedAt, serviceJar));
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    boolean matches(ProcessHandle handle, Path expectedJar) {
        if (pid != handle.pid() || !serviceJar.equals(normalize(expectedJar))) {
            return false;
        }
        return handle.info().startInstant().map(startedAt::equals).orElse(false);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing ownership property " + key);
        }
        return value;
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
