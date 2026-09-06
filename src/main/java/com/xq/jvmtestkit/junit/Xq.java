package com.xq.jvmtestkit.junit;

import com.xq.jvmtestkit.rest.RestApi;
import com.xq.jvmtestkit.rest.RestApiConfig;

import java.util.Objects;

/** Entry point for helpers owned by the current {@link XqTest} invocation. */
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
