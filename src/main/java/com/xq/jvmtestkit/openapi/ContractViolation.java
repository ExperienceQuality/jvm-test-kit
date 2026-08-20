package com.xq.jvmtestkit.openapi;

import java.util.Objects;

/**
 * One contract violation with sanitized diagnostics.
 */
public final class ContractViolation {
    private final String location;
    private final String message;

    public ContractViolation(String location, String message) {
        this.location = Objects.requireNonNull(location, "location");
        this.message = Objects.requireNonNull(message, "message");
    }

    public String location() {
        return location;
    }

    public String message() {
        return message;
    }

    public String sanitizedDiagnostics() {
        return "location=" + location + "; message=" + message;
    }
}
