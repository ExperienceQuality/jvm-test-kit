package example.spring;

import com.xq.jvmtestkit.cucumber.XqCucumberContext;
import example.support.CompanyOrderApi;
import io.cucumber.spring.ScenarioScope;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class CompanyUtilityConfiguration {
    @Bean
    @ScenarioScope
    CompanyOrderApi companyOrderApi(XqCucumberContext context) {
        return new CompanyOrderApi(context);
    }
}
