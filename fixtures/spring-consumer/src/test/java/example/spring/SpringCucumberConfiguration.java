package example.spring;

import com.xq.jvmtestkit.cucumber.spring.XqCucumberSpringTestConfiguration;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.test.context.ContextConfiguration;

/** Utility-only context: deliberately does not import or scan an AUT. */
@CucumberContextConfiguration
@ContextConfiguration(classes = {XqCucumberSpringTestConfiguration.class, CompanyUtilityConfiguration.class})
public class SpringCucumberConfiguration {
}
