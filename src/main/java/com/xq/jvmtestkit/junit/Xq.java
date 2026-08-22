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

    public static RestApi rest(String name, RestApiConfig config) {
        return XqContextHolder.current().rest(requireName(name), Objects.requireNonNull(config, "config"));
    }

    public static RestApi rest(String name) {
        return XqContextHolder.current().rest(requireName(name));
    }

    private static String requireName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("XQ helper name must not be blank");
        }
        return name;
    }
}
