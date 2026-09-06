import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.xq.jvmtestkit.junit.Xq;
import com.xq.jvmtestkit.junit.XqTest;
import com.xq.jvmtestkit.rest.RestApiConfig;
import com.xq.jvmtestkit.rest.RestRequest;
import com.xq.jvmtestkit.rest.RestResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@XqTest
class CleanConsumerTest {
    private HttpServer server;

    @BeforeEach
    void startServiceAndConfigureKit() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/routines", this::createRoutine);
        server.start();
        Xq.rest(RestApiConfig.at(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/")));
    }

    @AfterEach
    void stopService() {
        server.stop(0);
    }

    @Test
    void resolvesAndExercisesOnlyThePublishedApi() {
        RestResponse response = Xq.rest().post(
                "/routines",
                RestRequest.builder()
                        .header("X-User-Id", "user-1")
                        .jsonBody(Map.of("name", "Strength A"))
                        .build()
        );

        response.should().status(201).matchJson(Map.of("name", "Strength A"));
        assertTrue(response.bodyUtf8().contains("routine-1"));
    }

    private void createRoutine(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (!"POST".equals(exchange.getRequestMethod())
                || !"user-1".equals(exchange.getRequestHeaders().getFirst("X-User-Id"))
                || !body.contains("Strength A")) {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
            return;
        }
        byte[] response = "{\"id\":\"routine-1\",\"name\":\"Strength A\"}"
                .getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(201, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
