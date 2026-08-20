package com.xq.jvmtestkit.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceHttpClientTest {
    private final List<HttpServer> servers = new CopyOnWriteArrayList<>();

    @AfterEach
    void stopServers() {
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void resolvesServiceRelativeRequestsAndReturnsImmutableResponseValues() throws Exception {
        HttpServer server = server();
        server.createContext("/api/health", exchange -> respond(exchange, 200, "healthy", "X-Trace", "trace-42"));
        server.start();

        ServiceHttpClient client = new ServiceHttpClient(baseUri(server, "/api/"), HttpPolicy.defaults());
        byte[] requestBody = "request".getBytes(StandardCharsets.UTF_8);
        ServiceHttpRequest request = ServiceHttpRequest.post("/health")
                .header("Accept", "text/plain")
                .body(requestBody)
                .build();
        requestBody[0] = 'X';

        ServiceHttpResponse response = client.execute(request);

        assertEquals(200, response.statusCode());
        assertEquals("healthy", response.bodyUtf8());
        assertEquals("trace-42", header(response, "x-trace"));
        assertArrayEquals("request".getBytes(StandardCharsets.UTF_8), request.body());
        assertThrows(UnsupportedOperationException.class, () -> response.headers().put("new", List.of("value")));
        byte[] copiedResponseBody = response.body();
        copiedResponseBody[0] = 'X';
        assertEquals("healthy", response.bodyUtf8());
    }

    @Test
    void keepsDiagnosticsFreeOfBodiesAndSensitiveHeaderValues() throws Exception {
        HttpServer server = server();
        server.createContext("/secret", exchange -> respond(exchange, 401, "response-secret", "Set-Cookie", "session=response-token"));
        server.start();

        ServiceHttpResponse response = new ServiceHttpClient(baseUri(server, "/"), HttpPolicy.defaults())
                .execute(ServiceHttpRequest.get("/secret").header("Authorization", "Bearer request-token").build());

        String diagnostics = response.sanitizedDiagnostics();
        assertTrue(diagnostics.contains("status=401"));
        assertTrue(diagnostics.toLowerCase(Locale.ROOT).contains("set-cookie=<redacted>"));
        assertFalse(diagnostics.contains("response-secret"));
        assertFalse(diagnostics.contains("response-token"));
        assertFalse(diagnostics.contains("request-token"));
    }

    @Test
    void rejectsRedirectsOutsideConfiguredServiceBaseWithoutLeakingQueryValues() throws Exception {
        HttpServer service = server();
        HttpServer external = server();
        service.createContext("/api/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", baseUri(external, "/").resolve("stolen?token=do-not-leak").toString());
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        service.start();
        external.start();

        IOException exception = assertThrows(IOException.class, () -> new ServiceHttpClient(baseUri(service, "/api/"), HttpPolicy.defaults())
                .execute(ServiceHttpRequest.get("/redirect?secret=request-secret").build()));

        assertEquals("Service HTTP redirect is outside the configured service base URI", exception.getMessage());
        assertFalse(exception.getMessage().contains("do-not-leak"));
        assertFalse(exception.getMessage().contains("request-secret"));
    }

    @Test
    void enforcesExplicitRequestResponseAndRedirectLimits() throws Exception {
        HttpServer server = server();
        server.createContext("/large", exchange -> respond(exchange, 200, "response-body-is-too-large"));
        server.createContext("/again", exchange -> {
            exchange.getResponseHeaders().add("Location", "/again?secret=redirect-secret");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();

        HttpPolicy policy = HttpPolicy.builder()
                .requestTimeout(Duration.ofSeconds(1))
                .maxRequestBodyBytes(3)
                .maxResponseBodyBytes(3)
                .maxRedirects(0)
                .build();
        ServiceHttpClient client = new ServiceHttpClient(baseUri(server, "/"), policy);

        IOException requestLimit = assertThrows(IOException.class,
                () -> client.execute(ServiceHttpRequest.post("/large").bodyUtf8("more").build()));
        assertEquals("Service HTTP request body exceeds configured limit", requestLimit.getMessage());

        IOException responseLimit = assertThrows(IOException.class,
                () -> client.execute(ServiceHttpRequest.get("/large").build()));
        assertEquals("Service HTTP response body exceeds configured limit", responseLimit.getMessage());

        IOException redirectLimit = assertThrows(IOException.class,
                () -> client.execute(ServiceHttpRequest.get("/again").build()));
        assertEquals("Service HTTP redirect limit exceeded", redirectLimit.getMessage());
        assertFalse(redirectLimit.getMessage().contains("redirect-secret"));
    }

    @Test
    void rejectsUnsafeBaseAndRequestUrisBeforeAnyNetworkCall() {
        assertThrows(IllegalArgumentException.class,
                () -> new ServiceHttpClient(URI.create("https://example.test/api?token=unsafe"), HttpPolicy.defaults()));
        assertThrows(IllegalArgumentException.class,
                () -> ServiceHttpRequest.get("//other.test/path").build());
        assertThrows(IllegalArgumentException.class,
                () -> ServiceHttpRequest.get("/a/../admin").build());
        assertThrows(IllegalArgumentException.class,
                () -> HttpPolicy.builder().maxRedirects(21));
    }

    private HttpServer server() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servers.add(server);
        return server;
    }

    private static URI baseUri(HttpServer server, String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private static void respond(HttpExchange exchange, int status, String body, String... header) throws IOException {
        if (header.length == 2) {
            exchange.getResponseHeaders().add(header[0], header[1]);
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String header(ServiceHttpResponse response, String name) {
        return response.headers().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst()
                .orElseThrow();
    }
}
