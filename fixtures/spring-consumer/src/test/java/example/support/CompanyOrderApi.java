package example.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.xq.jvmtestkit.cucumber.XqCucumberContext;
import com.xq.jvmtestkit.rest.RestRequest;
import com.xq.jvmtestkit.rest.RestResponse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Example company-owned API utility, supplied by Spring per Cucumber scenario. */
public class CompanyOrderApi {
    private final XqCucumberContext context;
    private HttpServer server;
    private String receivedBody;

    public CompanyOrderApi(XqCucumberContext context) {
        this.context = context;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 18082), 0);
        server.createContext("/orders", this::receive);
        server.start();
    }

    public RestResponse submit() {
        return context.rest().post("/orders", RestRequest.builder()
                .jsonBody(Map.of(
                        "customer", Map.of("id", "spring-customer"),
                        "items", List.of(Map.of("sku", "SKU-S"))))
                .build());
    }

    public String receivedBody() {
        return receivedBody;
    }

    public XqCucumberContext context() {
        return context;
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private void receive(HttpExchange exchange) throws IOException {
        receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        byte[] response = ("{\"id\":\"spring-order-1\",\"status\":\"accepted\","
                + "\"customer\":{\"id\":\"spring-customer\",\"name\":\"Rowan\",\"vip\":false},"
                + "\"items\":[{\"sku\":\"SKU-S\",\"quantity\":1},{\"sku\":\"SKU-T\",\"quantity\":3}],"
                + "\"metadata\":{\"source\":\"spring-demo\",\"traceId\":\"private-spring-trace\"}}")
                .getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(201, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
