package com.xq.jvmtestkit.junit;

final class XqContextHolder {
    private static final ThreadLocal<XqTestContext> CURRENT = new ThreadLocal<>();

    private XqContextHolder() {
    }

    static void bind(XqTestContext context) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("An XQ test context is already active on this thread");
        }
        CURRENT.set(context);
    }

    static XqTestContext current() {
        XqTestContext context = CURRENT.get();
        if (context == null) {
            throw new IllegalStateException("No XQ test context is active; annotate the test class with @XqTest");
        }
        return context;
    }

    static void unbind(XqTestContext expected) {
        if (CURRENT.get() == expected) {
            CURRENT.remove();
        }
    }
}
