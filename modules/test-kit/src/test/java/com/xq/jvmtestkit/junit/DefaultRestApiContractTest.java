package com.xq.jvmtestkit.junit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.xq.jvmtestkit.rest.RestApiConfig;
import com.xq.jvmtestkit.rest.RestRequest;
import com.xq.jvmtestkit.rest.RestResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultRestApiContractTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsHeadersAndJsonForPostAndReturnsIndependentResponse() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> user = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/routines", exchange -> {
            method.set(exchange.getRequestMethod());
            user.set(exchange.getRequestHeaders().getFirst("X-User-Id"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("X-Trace", "trace-1");
            respond(exchange, 201, "{\"id\":\"routine-1\",\"name\":\"Strength A\"}");
        });
        server.start();

        try (DefaultRestApi api = new DefaultRestApi(RestApiConfig.at(baseUri("/api/")))) {
            RestResponse response = api.post(
                    "/routines",
                    RestRequest.builder()
                            .header("X-User-Id", "user-1")
                            .jsonBody(Map.of("name", "Strength A"))
                            .build()
            );

            response.should().status(201).matchJson(Map.of("name", "Strength A"));
            assertEquals("POST", method.get());
            assertEquals("user-1", user.get());
            assertEquals("{\"name\":\"Strength A\"}", requestBody.get());
            assertEquals("routine-1", com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                    .readTree(response.bodyUtf8()).path("id").asText());
        }
    }

    @Test
    void supportsGetAndPutAndRejectsUnsafeOrBodyBearingGet() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/item", exchange -> respond(exchange, 200,
                "{\"method\":\"" + exchange.getRequestMethod() + "\"}"));
        server.start();

        try (DefaultRestApi api = new DefaultRestApi(RestApiConfig.at(baseUri("/api/")))) {
            api.get("/item").should().status(200).matchJson("{\"method\":\"GET\"}");
            api.put("/item", RestRequest.builder().jsonBody(Map.of("value", 1)).build())
                    .should().status(200).matchJson("{\"method\":\"PUT\"}");
            assertThrows(IllegalArgumentException.class,
                    () -> api.get("/item", RestRequest.builder().jsonBody(Map.of("bad", true)).build()));
            assertThrows(IllegalArgumentException.class, () -> api.get("//outside.test/item"));
            assertThrows(IllegalArgumentException.class, () -> api.get("/a/../item"));
        }
    }

    @Test
    void refusesCallsAfterInvocationClose() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        DefaultRestApi api = new DefaultRestApi(RestApiConfig.at(baseUri("/")));
        api.close();

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> api.get("/anything"));
        assertTrue(failure.getMessage().contains("closed"));
    }

    @Test
    void enforcesRequestAndResponseBodyLimitsWithoutLeakingBodies() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/large", exchange -> {
            byte[] response = new byte[2 * 1024 * 1024 + 1];
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try (DefaultRestApi api = new DefaultRestApi(RestApiConfig.at(baseUri("/")))) {
            IllegalArgumentException requestFailure = assertThrows(IllegalArgumentException.class,
                    () -> api.post("/large", RestRequest.builder()
                            .jsonBody("x".repeat(2 * 1024 * 1024))
                            .build()));
            assertTrue(requestFailure.getMessage().contains("2097152-byte limit"));

            IllegalStateException responseFailure = assertThrows(IllegalStateException.class,
                    () -> api.get("/large"));
            assertEquals("REST request failed; remote details are redacted", responseFailure.getMessage());
        }
    }

    private URI baseUri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
