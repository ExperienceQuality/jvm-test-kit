package com.xq.jvmtestkit.junit;

import com.xq.jvmtestkit.rest.RestApi;
import com.xq.jvmtestkit.db.DatabaseRegistry;
import com.xq.jvmtestkit.stub.StubApi;
import com.xq.jvmtestkit.stub.StubRuntime;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;

final class XqTestContext implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean();
    private DefaultRestApi restApi;
    private StubApi stubApi;
    private final String scenarioId = UUID.randomUUID().toString();
    private final XqConfiguration configuration;
    private final DatabaseRegistry databases = DatabaseConfiguration.load(Thread.currentThread().getContextClassLoader());

    DatabaseRegistry db() { ensureOpen(); return databases; }

    XqTestContext() {
        configuration = XqConfiguration.load(Thread.currentThread().getContextClassLoader());
        restBaseUri = configuration.restBaseUri();
        if (configuration.stub().enabled()) stub();
    }

    private final java.net.URI restBaseUri;

    RestApi rest() {
        ensureOpen();
        if (restApi == null) {
            restApi = new DefaultRestApi(restBaseUri, this::stubHeaders);
        }
        return restApi;
    }

    private java.util.Map<String, String> stubHeaders() {
        if (stubApi == null || !configuration.stub().isolateScenarios()) return java.util.Map.of();
        return java.util.Map.of(StubApi.TEST_ID_HEADER, scenarioId);
    }

    StubApi stub() {
        ensureOpen();
        if (stubApi == null) {
            XqConfiguration.StubSettings settings = configuration.stub();
            stubApi = StubRuntime.open(scenarioId, settings.host(), settings.port(),
                    settings.resetBeforeScenario(), settings.isolateScenarios());
        }
        return stubApi.start();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (stubApi != null) stubApi.close();
            if (restApi != null) restApi.close();
        }
        stubApi = null;
        restApi = null;
        databases.close();
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("XQ test context is closed");
        }
    }
}
