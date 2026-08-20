package com.xq.jvmtestkit.openapi;

import java.util.List;

/**
 * Result of validating a response against an {@link OpenApiResponseContract}.
 */
public final class ContractCheck {
    private final boolean successful;
    private final List<ContractViolation> violations;

    private ContractCheck(boolean successful, List<ContractViolation> violations) {
        this.successful = successful;
        this.violations = List.copyOf(violations);
    }

    public static ContractCheck success() {
        return new ContractCheck(true, List.of());
    }

    public static ContractCheck failure(List<ContractViolation> violations) {
        if (violations.isEmpty()) {
            throw new IllegalArgumentException("failed check requires at least one violation");
        }
        return new ContractCheck(false, violations);
    }

    public boolean passed() {
        return successful;
    }

    public List<ContractViolation> violations() {
        return violations;
    }
}
