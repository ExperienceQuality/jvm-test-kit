package com.xq.jvmtestkit.junit;

import com.xq.jvmtestkit.contract.RestApi;
import com.xq.jvmtestkit.contract.RestApiConfig;
import com.xq.jvmtestkit.impl.RestApiService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

final class XqTestContext implements AutoCloseable {
    private final Map<String, NamedRestApi> restApis = new LinkedHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    RestApi rest(String name, RestApiConfig config) {
        ensureOpen();
        NamedRestApi existing = restApis.get(name);
        if (existing != null) {
            if (!existing.config().equals(config)) {
                throw new IllegalStateException(
                        "REST helper '" + name + "' is already configured differently in this XQ test invocation"
                );
            }
            return existing.service();
        }

        RestApiService service = new RestApiService(config);
        restApis.put(name, new NamedRestApi(config, service));
        return service;
    }

    RestApi rest(String name) {
        ensureOpen();
        NamedRestApi existing = restApis.get(name);
        if (existing == null) {
            throw new IllegalStateException(
                    "No REST helper named '" + name + "' is configured; "
                            + "call Xq.rest(\"" + name + "\", config) from an @BeforeEach hook first"
            );
        }
        return existing.service();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        List<NamedRestApi> resources = new ArrayList<>(restApis.values());
        for (int index = resources.size() - 1; index >= 0; index--) {
            resources.get(index).service().close();
        }
        restApis.clear();
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("XQ test context is closed");
        }
    }

    private record NamedRestApi(RestApiConfig config, RestApiService service) {
    }
}
