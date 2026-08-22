package com.xq.jvmtestkit.impl;

import com.xq.jvmtestkit.contract.RestApi;
import com.xq.jvmtestkit.contract.RestApiConfig;
import com.xq.jvmtestkit.dto.RestResponse;
import com.xq.jvmtestkit.http.ServiceHttpClient;
import com.xq.jvmtestkit.http.ServiceHttpRequest;
import com.xq.jvmtestkit.http.ServiceHttpResponse;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RestApiService implements RestApi, AutoCloseable {
    private final ServiceHttpClient client;
    private final AtomicBoolean closed = new AtomicBoolean();
    private RestMatcher matcher;

    public RestApiService(RestApiConfig config) {
        Objects.requireNonNull(config, "config");
        this.client = new ServiceHttpClient(config.serviceBaseUri(), config.httpPolicy());
    }

    @Override
    public RestApi get(String path) {
        return execute(ServiceHttpRequest.get(path).build());
    }

    @Override
    public RestApi post(String path) {
        return execute(ServiceHttpRequest.post(path).build());
    }

    @Override
    public RestMatcher should() {
        ensureOpen();
        if (matcher == null) {
            throw new IllegalStateException("No REST response is available; execute get() or post() before should()");
        }
        return this.matcher;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            matcher = null;
        }
    }

    private RestApi execute(ServiceHttpRequest request) {
        ensureOpen();
        try {
            pipe(client.execute(request));
            return this;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("REST request was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("REST request failed: " + exception.getMessage(), exception);
        }
    }

    private void pipe(ServiceHttpResponse response) {
        RestResponse res = new RestResponse(response.statusCode(), response.headers(), response.body());
        this.matcher = new RestMatcherService(res);
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("REST helper is closed because its XQ test invocation has finished");
        }
    }
}
