package com.xq.jvmtestkit.cucumber.spring;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.test.context.ContextConfiguration;

/**
 * Reusable Cucumber/Spring configuration for JVM Test Kit consumers.
 *
 * <p>Consumers can extend this class and add their own test utility
 * configuration with {@link ContextConfiguration}.</p>
 */
@CucumberContextConfiguration
@ContextConfiguration(classes = XqCucumberSpringTestConfiguration.class)
public abstract class XqCucumberSpringConfiguration {
}
