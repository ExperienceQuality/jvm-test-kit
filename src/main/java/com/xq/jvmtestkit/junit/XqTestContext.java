package com.xq.jvmtestkit.junit;

import com.xq.jvmtestkit.contract.RestApi;
import com.xq.jvmtestkit.contract.RestApiConfig;
import com.xq.jvmtestkit.impl.RestApiService;

import java.util.concurrent.atomic.AtomicBoolean;

final class XqTestContext implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean();
    private RestApiConfig restConfig;
    private RestApiService restApi;

    RestApi rest(RestApiConfig config) {
        ensureOpen();
        if (restApi != null) {
            if (!restConfig.equals(config)) {
                throw new IllegalStateException(
                        "The REST API is already configured differently in this XQ test invocation"
                );
            }
            return restApi;
        }

        restConfig = config;
        restApi = new RestApiService(config);
        return restApi;
    }

    RestApi rest() {
        ensureOpen();
        if (restApi == null) {
            throw new IllegalStateException(
                    "No REST API is configured; call Xq.rest(config) from an @BeforeEach hook first"
            );
        }
        return restApi;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (restApi != null) {
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
