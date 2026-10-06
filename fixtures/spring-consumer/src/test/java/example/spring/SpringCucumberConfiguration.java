package example.spring;

import com.xq.jvmtestkit.cucumber.spring.XqCucumberSpringConfiguration;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.test.context.ContextConfiguration;

/** Adds consumer-owned utility beans to the shared JVM Test Kit Spring context. */
@CucumberContextConfiguration
@ContextConfiguration(classes = CompanyUtilityConfiguration.class)
public class SpringCucumberConfiguration extends XqCucumberSpringConfiguration {
}
