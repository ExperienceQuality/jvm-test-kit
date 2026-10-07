package com.xq.jvmtestkit.cucumber;

/** Bridges the active Cucumber scenario to the static Xq entry point. */
public final class XqCucumberStubHolder {
    private static final ThreadLocal<XqCucumberContext> CURRENT = new ThreadLocal<>();

    private XqCucumberStubHolder() {
    }

    public static void bind(XqCucumberContext context) {
        CURRENT.set(context);
    }

    public static XqCucumberContext current() {
        XqCucumberContext context = CURRENT.get();
        if (context == null) throw new IllegalStateException("No XQ Cucumber scenario is active");
        return context;
    }

    public static void unbind(XqCucumberContext expected) {
        if (CURRENT.get() == expected) CURRENT.remove();
    }
}
