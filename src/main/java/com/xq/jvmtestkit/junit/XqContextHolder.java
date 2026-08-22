package com.xq.jvmtestkit.junit;

import java.util.Objects;

final class XqContextHolder {
    private static final ThreadLocal<XqTestContext> CURRENT = new ThreadLocal<>();

    private XqContextHolder() {
    }

    static XqTestContext current() {
        XqTestContext context = CURRENT.get();
        if (context == null) {
            throw new IllegalStateException(
                    "No XQ test context is active; annotate the test class with @XqTest "
                            + "and call Xq only from its hooks or test methods"
            );
        }
        return context;
    }

    static void bind(XqTestContext context) {
        Objects.requireNonNull(context, "context");
        if (CURRENT.get() != null) {
            throw new IllegalStateException("An XQ test context is already active on this thread");
        }
        CURRENT.set(context);
    }

    static void unbind(XqTestContext expected) {
        XqTestContext current = CURRENT.get();
        if (current == null) {
            return;
        }
        if (current != expected) {
            throw new IllegalStateException("Attempted to unbind a different XQ test context");
        }
        CURRENT.remove();
    }
}
