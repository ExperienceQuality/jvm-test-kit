package com.xq.jvmtestkit.cucumber;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;

/** Starts and closes company resources at each Cucumber scenario boundary. */
public final class XqCucumberHooks {
    private final XqCucumberContext context;

    public XqCucumberHooks(XqCucumberContext context) {
        this.context = context;
    }

    @Before(order = Integer.MIN_VALUE)
    public void beforeScenario(Scenario scenario) {
        context.start(scenario);
    }

    @After(order = Integer.MAX_VALUE)
    public void afterScenario(Scenario scenario) {
        context.close();
    }
}
