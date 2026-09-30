package com.xq.jvmtestkit.junit;

import com.xq.jvmtestkit.rest.RestApi;
import com.xq.jvmtestkit.db.DatabaseRegistry;

import java.util.concurrent.atomic.AtomicBoolean;

final class XqTestContext implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean();
    private DefaultRestApi restApi;
    private final DatabaseRegistry databases = DatabaseConfiguration.load(Thread.currentThread().getContextClassLoader());

    DatabaseRegistry db() { ensureOpen(); return databases; }

    XqTestContext() {
        XqConfiguration configuration = XqConfiguration.load(Thread.currentThread().getContextClassLoader());
        restBaseUri = configuration.restBaseUri();
    }

    private final java.net.URI restBaseUri;

    RestApi rest() {
        ensureOpen();
        if (restApi == null) {
            restApi = new DefaultRestApi(restBaseUri);
        }
        return restApi;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && restApi != null) {
            restApi.close();
        }
        restApi = null;
        databases.close();
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("XQ test context is closed");
        }
    }
}
