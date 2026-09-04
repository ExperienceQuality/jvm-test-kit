package com.xq.jvmtestkit.contract;

import com.sun.net.httpserver.HttpServer;
import com.xq.jvmtestkit.impl.RestApiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestApiFluentTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void statusAndJsonAssertionsValidateResponseThroughPublicApi() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/users", exchange -> {
            byte[] body = "{\"id\":1,\"name\":\"Ada\"}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try (RestApiService service = new RestApiService(RestApiConfig.at(baseUri()))) {
            RestApi api = service;
            api.get("/users").should()
                    .status(200)
                    .equalToJson("{\"name\":\"Ada\",\"id\":1}");
        }
    }

    @Test
    void failedAssertionThrowsAssertionError() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/users", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();

        try (RestApiService service = new RestApiService(RestApiConfig.at(baseUri()))) {
            RestApi api = service;
            assertThrows(AssertionError.class, () -> api.get("/users").should().status(200));
        }
    }

    @Test
    void jsonFailureShowsFriendlyExpectedActualDiff() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/users", exchange -> {
            byte[] body = "{\"id\":1,\"name\":\"Grace\"}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try (RestApiService service = new RestApiService(RestApiConfig.at(baseUri()))) {
            RestApi api = service;
            AssertionError failure = assertThrows(AssertionError.class,
                    () -> api.get("/users").should().equalToJson("{\"id\":1,\"name\":\"Ada\"}"));
            assertTrue(failure.getMessage().contains("-   \"name\" : \"Ada\""));
            assertTrue(failure.getMessage().contains("+   \"name\" : \"Grace\""));
        }
    }

    @Test
    void matchIgnoresArrayOrderAndAllowsActualExtras() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/users", exchange -> {
            byte[] body = "{\"users\":[{\"id\":2},{\"id\":1}],\"trace\":\"ignored\"}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try (RestApiService service = new RestApiService(RestApiConfig.at(baseUri()))) {
            RestApi api = service;
            api.get("/users").should().match("{\"users\":[{\"id\":1},{\"id\":2}]}").status(200);
        }
    }

    @Test
    void matchFailsWhenExpectedArrayElementIsMissing() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/users", exchange -> {
            byte[] body = "{\"users\":[{\"id\":1}]}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try (RestApiService service = new RestApiService(RestApiConfig.at(baseUri()))) {
            RestApi api = service;
            assertThrows(AssertionError.class,
                    () -> api.get("/users").should().match("{\"users\":[{\"id\":1},{\"id\":2}]}").status(200));
        }
    }

    private URI baseUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }
}
