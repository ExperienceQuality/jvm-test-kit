package example.steps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import example.support.CompanyOrderApi;
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
    private static final ObjectMapper JSON = new ObjectMapper();
    private final CompanyOrderApi companyApi;
    private int status;

    public SpringOrderSteps(CompanyOrderApi companyApi) {
        this.companyApi = companyApi;
    }

    @Given("the in-process order API is available")
    public void startApi() throws IOException {
        companyApi.start();
    }

    @When("I submit an order through the company utility")
    public void submitOrder() {
        status = companyApi.submit().statusCode();
    }

    @Then("the API receives the order and the company scenario context is active")
    public void verifyOrderAndContext() throws IOException {
        assertEquals(201, status);
        JsonNode sent = JSON.readTree(companyApi.receivedBody());
        assertEquals("spring-customer", sent.at("/customer/id").asText());
        assertEquals("SKU-S", sent.at("/items/0/sku").asText());
        assertEquals("submit a request through the Spring-managed company API utility",
                companyApi.context().scenario().name());
        assertEquals("127.0.0.1", companyApi.context().baseUri().getHost());
        assertFalse(companyApi.context().runId().isBlank());
        assertNotNull(companyApi.context());
        assertTrue(companyApi.receivedBody().contains("spring-customer"));
    }

    @After
    public void stopApi() {
        companyApi.stop();
    }
}
