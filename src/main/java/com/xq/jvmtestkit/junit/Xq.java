package com.xq.jvmtestkit.junit;

import com.xq.jvmtestkit.contract.RestApi;
import com.xq.jvmtestkit.contract.RestApiConfig;

import java.util.Objects;

/**
 * Static author-facing facade for helpers owned by the current XQ test invocation.
 */
public final class Xq {
    private Xq() {
    }

    public static RestApi rest(RestApiConfig config) {
        return XqContextHolder.current().rest(Objects.requireNonNull(config, "config"));
    }

    public static RestApi rest() {
        return XqContextHolder.current().rest();
    }
}
