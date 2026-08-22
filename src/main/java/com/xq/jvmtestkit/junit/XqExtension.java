package com.xq.jvmtestkit.junit;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Adapts the JUnit per-invocation lifecycle to the XQ runtime context.
 */
public final class XqExtension implements BeforeEachCallback, AfterEachCallback {
    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(XqExtension.class);
    private static final String CONTEXT_KEY = "xq-test-context";

    @Override
    public void beforeEach(ExtensionContext context) {
        XqTestContext testContext = new XqTestContext();
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        store.put(CONTEXT_KEY, testContext);
        try {
            XqContextHolder.bind(testContext);
        } catch (RuntimeException exception) {
            store.remove(CONTEXT_KEY);
            testContext.close();
            throw exception;
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        XqTestContext testContext = context.getStore(NAMESPACE)
                .remove(CONTEXT_KEY, XqTestContext.class);
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
