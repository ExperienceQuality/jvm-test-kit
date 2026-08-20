package com.xq.jvmtestkit.openapi;

import java.util.Objects;

/**
 * Identifies one HTTP operation in an OpenAPI document.
 */
public final class Operation {
    private final String path;
    private final String method;
    private final String operationId;

    public Operation(String path, String method, String operationId) {
        this.path = requirePath(path);
        this.method = requireMethod(method);
        this.operationId = operationId == null ? "" : operationId;
    }

    public String path() {
        return path;
    }

    public String method() {
        return method;
    }

    public String operationId() {
        return operationId;
    }

    private static String requirePath(String path) {
        Objects.requireNonNull(path, "path");
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("path must start with /");
        }
        return path;
    }

    private static String requireMethod(String method) {
        Objects.requireNonNull(method, "method");
        String normalized = method.toUpperCase(java.util.Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("method must not be blank");
        }
        return normalized;
    }
}
