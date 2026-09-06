package com.xq.jvmtestkit.junit;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Adapts JUnit's per-invocation lifecycle to the XQ context. */
public final class XqExtension implements BeforeEachCallback, AfterEachCallback {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(XqExtension.class);
    private static final String CONTEXT_KEY = "xq-test-context";

    @Override
    public void beforeEach(ExtensionContext context) {
        XqTestContext testContext = new XqTestContext();
        context.getStore(NAMESPACE).put(CONTEXT_KEY, testContext);
        try {
            XqContextHolder.bind(testContext);
        } catch (RuntimeException exception) {
            context.getStore(NAMESPACE).remove(CONTEXT_KEY);
            testContext.close();
            throw exception;
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        XqTestContext testContext = context.getStore(NAMESPACE).remove(CONTEXT_KEY, XqTestContext.class);
        if (testContext == null) {
            return;
        }
        try {
            XqContextHolder.unbind(testContext);
        } finally {
            testContext.close();
        }
    }
}
