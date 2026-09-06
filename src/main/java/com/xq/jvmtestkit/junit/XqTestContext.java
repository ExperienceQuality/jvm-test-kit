package com.xq.jvmtestkit.junit;

import com.xq.jvmtestkit.rest.RestApi;
import com.xq.jvmtestkit.rest.RestApiConfig;

import java.util.concurrent.atomic.AtomicBoolean;

final class XqTestContext implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean();
    private RestApiConfig restConfig;
    private DefaultRestApi restApi;

    RestApi rest(RestApiConfig config) {
        ensureOpen();
        if (restApi == null) {
            restConfig = config;
            restApi = new DefaultRestApi(config);
        } else if (!restConfig.equals(config)) {
            throw new IllegalStateException("The REST API is already configured differently in this XQ test invocation");
        }
        return restApi;
    }

    RestApi rest() {
        ensureOpen();
        if (restApi == null) {
            throw new IllegalStateException("No REST API is configured; call Xq.rest(config) from @BeforeEach first");
        }
        return restApi;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && restApi != null) {
            restApi.close();
        }
        restApi = null;
        restConfig = null;
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("XQ test context is closed");
        }
    }
}
