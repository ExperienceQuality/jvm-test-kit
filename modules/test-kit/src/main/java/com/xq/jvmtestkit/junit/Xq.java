package com.xq.jvmtestkit.junit;

import com.xq.jvmtestkit.rest.RestApi;
import com.xq.jvmtestkit.db.DatabaseRegistry;

/** Entry point for helpers owned by the current {@link XqTest} invocation. */
public final class Xq {
    private Xq() {
    }

    public static RestApi rest() {
        return XqContextHolder.current().rest();
    }

    public static DatabaseRegistry db() {
        return XqContextHolder.current().db();
    }
}
