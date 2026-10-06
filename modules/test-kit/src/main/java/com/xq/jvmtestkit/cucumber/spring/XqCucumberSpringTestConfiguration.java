package com.xq.jvmtestkit.cucumber.spring;

import com.xq.jvmtestkit.cucumber.XqCucumberContext;
import io.cucumber.spring.ScenarioScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Spring test utility context; deliberately contains no application beans. */
@TestConfiguration(proxyBeanMethods = false)
public class XqCucumberSpringTestConfiguration {
    @Bean
    @ScenarioScope
    public XqCucumberContext xqCucumberContext() {
        return new XqCucumberContext();
    }
}
