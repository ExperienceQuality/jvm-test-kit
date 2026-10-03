package example.steps;

import com.xq.jvmtestkit.cucumber.XqJsonTable;
import example.support.CompanyOrderApi;
import com.xq.jvmtestkit.rest.RestResponse;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpringOrderSteps {
    private final CompanyOrderApi companyApi;
    private RestResponse response;

    public SpringOrderSteps(CompanyOrderApi companyApi) {
        this.companyApi = companyApi;
    }

    @Given("the in-process order API is available")
    public void startApi() throws IOException {
        companyApi.start();
    }

    @When("I submit an order through the company utility and it is accepted")
    public void submitOrder() {
        response = companyApi.submit();
        response.should().hasStatus(201);
    }

    @Then("the created order response includes:")
    public void verifyOrderResponse(DataTable table) {
        response.should()
                .hasJsonPathValue("$.status", "accepted")
                .containsJson(XqJsonTable.compose(table));
        assertEquals("submit a request and assert its response through Spring-managed company utilities",
                companyApi.context().scenario().name());
        assertEquals("127.0.0.1", companyApi.context().baseUri().getHost());
        assertFalse(companyApi.context().runId().isBlank());
        assertNotNull(companyApi.context());
        assertTrue(companyApi.receivedBody().contains("spring-customer"));
    }

    @Then("its customer details contain:")
    public void verifyCustomerDetails(DataTable table) {
        response.should().containsJsonAtPath("$.customer", XqJsonTable.compose(table));
    }

    @Then("its returned items include:")
    public void verifyReturnedItems(DataTable table) {
        response.should().containsJsonAtPath("$.items", XqJsonTable.compose(table));
    }

    @After
    public void stopApi() {
        companyApi.stop();
    }
}
