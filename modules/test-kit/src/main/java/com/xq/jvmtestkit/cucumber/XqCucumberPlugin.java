package com.xq.jvmtestkit.cucumber;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.TestCase;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Emits redacted scenario lifecycle events as newline-delimited JSON when configured. */
public final class XqCucumberPlugin implements ConcurrentEventListener {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<Path, Object> OUTPUT_LOCKS = new ConcurrentHashMap<>();
    private final String executionId = UUID.randomUUID().toString();
    private final Map<String, String> scenarioIds = new ConcurrentHashMap<>();
    private final Path output;

    public XqCucumberPlugin() {
        String configured = System.getenv("XQ_CUCUMBER_EVENTS_FILE");
        output = configured == null || configured.isBlank() ? null : Path.of(configured);
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseStarted.class, this::onStarted);
        publisher.registerHandlerFor(TestCaseFinished.class, this::onFinished);
    }

    private void onStarted(TestCaseStarted event) {
        TestCase testCase = event.getTestCase();
        String id = UUID.randomUUID().toString();
        scenarioIds.put(testCase.getId().toString(), id);
        write(event("scenario.started", testCase, id, null, null));
    }

    private void onFinished(TestCaseFinished event) {
        TestCase testCase = event.getTestCase();
        String id = scenarioIds.remove(testCase.getId().toString());
        if (id == null) id = UUID.randomUUID().toString();
        write(event("scenario.finished", testCase, id, event.getResult().getStatus().name(),
                event.getResult().getDuration() == null ? null : event.getResult().getDuration().toMillis()));
    }

    private Map<String, Object> event(String type, TestCase testCase, String scenarioId, String status, Long durationMs) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", 1);
        value.put("event", type);
        value.put("executionId", executionId);
        value.put("scenarioId", scenarioId);
        value.put("scenarioName", testCase.getName());
        value.put("featureUri", testCase.getUri().toString());
        value.put("line", testCase.getLocation().getLine());
        value.put("tags", testCase.getTags());
        value.put("timestamp", Instant.now().toString());
        if (status != null) value.put("status", status);
        if (durationMs != null) value.put("durationMs", durationMs);
        return value;
    }

    private void write(Map<String, Object> value) {
        if (output == null) return;
        try {
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            byte[] encoded = (JSON.writeValueAsString(value) + "\n").getBytes(StandardCharsets.UTF_8);
            Path absolute = output.toAbsolutePath().normalize();
            Object lock = OUTPUT_LOCKS.computeIfAbsent(absolute, ignored -> new Object());
            synchronized (lock) {
                try (FileChannel channel = FileChannel.open(output, StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);
                     FileLock ignored = channel.lock()) {
                    channel.position(channel.size());
                    ByteBuffer buffer = ByteBuffer.wrap(encoded);
                    while (buffer.hasRemaining()) channel.write(buffer);
                }
            }
        } catch (IOException exception) {
            System.err.println("XQ Cucumber event output failed; scenario results remain authoritative ("
                    + exception.getClass().getSimpleName() + ")");
        }
    }
}
