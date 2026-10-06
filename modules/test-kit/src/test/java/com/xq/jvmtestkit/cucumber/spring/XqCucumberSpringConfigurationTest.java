package com.xq.jvmtestkit.cucumber.spring;

import com.xq.jvmtestkit.cucumber.XqCucumberContext;
import io.cucumber.spring.ScenarioScope;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XqCucumberSpringConfigurationTest {
    @Test
    void usesOnlyTheDedicatedCompanyUtilityContext() {
        assertNotNull(XqCucumberSpringTestConfiguration.class.getAnnotation(TestConfiguration.class));
    }

    @Test
    void exposesXqContextAsScenarioScopedSpringBean() throws NoSuchMethodException {
        Method method = XqCucumberSpringTestConfiguration.class.getMethod("xqCucumberContext");
        assertEquals(XqCucumberContext.class, method.getReturnType());
        assertNotNull(method.getAnnotation(Bean.class));
        assertNotNull(method.getAnnotation(ScenarioScope.class));
        assertTrue(Arrays.stream(XqCucumberSpringTestConfiguration.class.getDeclaredMethods())
                .anyMatch(candidate -> candidate.getName().equals("xqCucumberContext")));
    }
}
