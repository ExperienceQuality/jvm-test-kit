package com.xq.jvmtestkit.junit;

import com.xq.jvmtestkit.rest.RestApi;
import com.xq.jvmtestkit.db.DatabaseRegistry;
import com.xq.jvmtestkit.stub.StubApi;
import com.xq.jvmtestkit.cucumber.XqCucumberStubHolder;

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

    public static StubApi stub() {
        if (XqContextHolder.isActive()) {
            return XqContextHolder.current().stub();
        }
        return XqCucumberStubHolder.current().stub();
    }
}
