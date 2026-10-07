package com.xq.jvmtestkit.cucumber;

import com.xq.jvmtestkit.config.ConfigurationManager;
import com.xq.jvmtestkit.rest.RestApi;
import com.xq.jvmtestkit.junit.DefaultRestApi;
import com.xq.jvmtestkit.stub.StubApi;
import com.xq.jvmtestkit.stub.StubRuntime;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import io.cucumber.java.Scenario;

/** Scenario-scoped XQ services supplied through the configured Cucumber object factory. */
public class XqCucumberContext implements AutoCloseable {
    private final String runId = UUID.randomUUID().toString();
    private URI baseUri;
    private ScenarioMetadata scenario;
    private RestApi rest;
    private StubApi stub;
    private boolean closed;
    private final ConfigurationManager.StubSettings stubConfiguration =
            ConfigurationManager.loadStubSettings(Thread.currentThread().getContextClassLoader());

    public XqCucumberContext() {
    }

    void start(Scenario source) {
        ensureOpen();
        scenario = new ScenarioMetadata(source.getName(), source.getUri().toString(), source.getLine(),
                Set.copyOf(source.getSourceTagNames()));
        String value = ConfigurationManager.getTestBaseUrl();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required property testBaseUrl in /xq.yaml");
        }
        try {
            baseUri = normalize(URI.create(value));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid property testBaseUrl in /xq.yaml", exception);
        }
        if (stubConfiguration.enabled()) stub();
        XqCucumberStubHolder.bind(this);
    }

    /** Returns the scenario's service-relative REST client. */
    public RestApi rest() {
        ensureOpen();
        if (baseUri == null) {
            throw new IllegalStateException("XQ Cucumber context has not started");
        }
        if (rest == null) {
            rest = new DefaultRestApi(baseUri, this::stubHeaders);
        }
        return rest;
    }

    private java.util.Map<String, String> stubHeaders() {
        if (stub == null || !stubConfiguration.isolateScenarios()) return java.util.Map.of();
        return java.util.Map.of(StubApi.TEST_ID_HEADER, runId);
    }

    /** Returns the scenario-scoped downstream stub. */
    public StubApi stub() {
        ensureOpen();
        if (scenario == null) throw new IllegalStateException("XQ Cucumber context has not started");
        if (stub == null) {
            stub = StubRuntime.open(runId, stubConfiguration.host(), stubConfiguration.port(),
                    stubConfiguration.resetBeforeScenario(), stubConfiguration.isolateScenarios());
        }
        return stub.start();
    }

    /** Opaque identifier shared by all events for this scenario. */
    public String runId() {
        return runId;
    }

    /** Resolved service base URI, available after the company Before hook. */
    public URI baseUri() {
        ensureOpen();
        if (baseUri == null) throw new IllegalStateException("XQ Cucumber context has not started");
        return baseUri;
    }

    /** Cucumber metadata for the active scenario. */
    public ScenarioMetadata scenario() {
        ensureOpen();
        if (scenario == null) throw new IllegalStateException("XQ Cucumber context has not started");
        return scenario;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            if (rest instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception exception) {
                    throw new IllegalStateException("Could not close XQ scenario REST client", exception);
                }
            }
            if (stub != null) stub.close();
        } finally {
            XqCucumberStubHolder.unbind(this);
            stub = null;
            rest = null;
        }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("XQ Cucumber context is closed");
    }

    private static URI normalize(URI input) {
        if (!input.isAbsolute() || input.getHost() == null || input.getRawUserInfo() != null
                || input.getRawQuery() != null || input.getRawFragment() != null
                || !("http".equalsIgnoreCase(input.getScheme()) || "https".equalsIgnoreCase(input.getScheme()))) {
            throw new IllegalArgumentException("base URI must be an absolute HTTP(S) URI without credentials, query, or fragment");
        }
        URI normalized = input.normalize();
        String path = normalized.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        else if (!path.endsWith("/")) path += "/";
        return URI.create(normalized.getScheme() + "://" + normalized.getRawAuthority() + path);
    }

    /** Immutable business-safe scenario metadata available to application steps. */
    public record ScenarioMetadata(String name, String featureUri, int line, Set<String> tags) {
        public ScenarioMetadata {
            tags = Set.copyOf(tags);
        }
    }
}
