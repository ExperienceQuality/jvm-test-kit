package example.steps;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xq.jvmtestkit.cucumber.XqCucumberContext;
import com.xq.jvmtestkit.cucumber.XqJsonTable;
import com.xq.jvmtestkit.rest.RestRequest;
import com.xq.jvmtestkit.rest.RestResponse;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class OrderSteps {
    private final XqCucumberContext test;
    private static final ObjectMapper JSON = new ObjectMapper();
    private String received;
    private HttpServer server;
    private RestResponse response;

    public OrderSteps(XqCucumberContext test) {
        this.test = test;
    }

    @Before(order = 0)
    public void startApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 18081), 0);
        server.createContext("/orders", this::receiveOrder);
        server.start();
    }

    @After(order = 0)
    public void stopApi() {
        if (server != null) server.stop(0);
    }

    @When("I submit this order and the service accepts it:")
    public void submitOrder(DataTable table) {
        JsonNode body = XqJsonTable.compose(table);
        response = test.rest().post("/orders", RestRequest.builder().jsonBody(body).build());
        response.should().hasStatus(201);
    }

    @Then("the created order response includes:")
    public void verifyOrderResponse(DataTable table) {
        assertNotNull(response);
        response.should()
                .hasJsonPathValue("$.status", "accepted")
                .containsJson(XqJsonTable.compose(table));
        verifyRequestWasReceived();
        assertEquals("submit a nested order and inspect the API response", test.scenario().name());
        assertEquals("127.0.0.1", test.baseUri().getHost());
        assertFalse(test.runId().isBlank());
    }

    @Then("its customer details contain:")
    public void verifyCustomerDetails(DataTable table) {
        response.should().containsJsonAtPath("$.customer", XqJsonTable.compose(table));
    }

    @Then("its returned items include:")
    public void verifyReturnedItems(DataTable table) {
        response.should().containsJsonAtPath("$.items", XqJsonTable.compose(table));
    }

    private void verifyRequestWasReceived() {
        assertNotNull(received);
        JsonNode json;
        try {
            json = JSON.readTree(received);
        } catch (IOException exception) {
            throw new AssertionError("API received invalid JSON", exception);
        }
        assertTrue(json.isArray());
        assertEquals(2, json.size());
        assertEquals("cust-123", json.get(0).at("/customer/id").asText());
        assertTrue(json.get(0).at("/customer/profile/vip").asBoolean());
        assertEquals("SKU-1", json.get(0).at("/items/0/sku").asText());
        assertEquals(2, json.get(0).at("/items/0/quantity").asInt());
        assertEquals("clean-consumer", json.get(0).at("/metadata/source").asText());
        assertEquals("cust-456", json.get(1).at("/customer/id").asText());
        assertEquals("SKU-2", json.get(1).at("/items/0/sku").asText());
        assertEquals("ui", json.get(1).at("/metadata/source").asText());
    }

    private void receiveOrder(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        received = body;
        byte[] response = ("{\"id\":\"order-123\",\"status\":\"accepted\","
                + "\"customer\":{\"id\":\"cust-123\",\"name\":\"Mina\",\"vip\":true},"
                + "\"items\":[{\"sku\":\"SKU-1\",\"quantity\":2},{\"sku\":\"SKU-2\",\"quantity\":1}],"
                + "\"metadata\":{\"source\":\"orders-service\",\"traceId\":\"private-trace\"}}")
                .getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(201, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
