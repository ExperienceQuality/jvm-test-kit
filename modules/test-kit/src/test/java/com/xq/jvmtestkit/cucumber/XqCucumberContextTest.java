package com.xq.jvmtestkit.cucumber;

import io.cucumber.plugin.event.EventHandler;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestRunFinished;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XqCucumberContextTest {
    @Test
    void eachScenarioContextOwnsAnOpaqueRunIdAndRejectsUseOutsideLifecycle() {
        XqCucumberContext first = new XqCucumberContext();
        XqCucumberContext second = new XqCucumberContext();
        assertNotEquals(first.runId(), second.runId());
        assertThrows(IllegalStateException.class, first::rest);
        assertThrows(IllegalStateException.class, first::baseUri);
        first.close();
        assertThrows(IllegalStateException.class, first::rest);
        second.close();
    }

    @Test
    void afterHookClosesScenarioResources() {
        XqCucumberContext context = new XqCucumberContext();
        new XqCucumberHooks(context).afterScenario(null);
        assertThrows(IllegalStateException.class, context::rest);
    }

    @Test
    void lifecyclePluginSubscribesToScenarioStartAndFinishEvents() {
        XqCucumberPlugin plugin = new XqCucumberPlugin();
        Set<Class<?>> eventTypes = new HashSet<>();
        plugin.setEventPublisher(new EventPublisher() {
            @Override
            public <T> void registerHandlerFor(Class<T> eventType, EventHandler<T> handler) {
                eventTypes.add(eventType);
            }

            @Override
            public <T> void removeHandlerFor(Class<T> eventType, EventHandler<T> handler) {
                eventTypes.remove(eventType);
            }
        });
        assertEquals(Set.of(TestCaseStarted.class, TestCaseFinished.class, TestRunFinished.class), eventTypes);
    }
}
