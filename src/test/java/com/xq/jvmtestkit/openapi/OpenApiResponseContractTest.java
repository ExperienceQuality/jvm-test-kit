package com.xq.jvmtestkit.openapi;

import com.sun.net.httpserver.HttpServer;
import com.xq.jvmtestkit.http.HttpPolicy;
import com.xq.jvmtestkit.http.ServiceHttpClient;
import com.xq.jvmtestkit.http.ServiceHttpRequest;
import com.xq.jvmtestkit.http.ServiceHttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiResponseContractTest {
    private final List<HttpServer> servers = new CopyOnWriteArrayList<>();

    @AfterEach
    void stopServers() {
        servers.forEach(server -> server.stop(0));
    }
    private static final String SPEC = """
            openapi: 3.0.3
            info:
              title: Pets
              version: 1.0.0
            paths:
              /pets:
                get:
                  operationId: listPets
                  responses:
                    '200':
                      content:
                        application/json:
                          schema:
                            type: array
                            items:
                              $ref: '#/components/schemas/Pet'
            components:
              schemas:
                Pet:
                  type: object
                  required: [id, name]
                  properties:
                    id:
                      type: integer
                    name:
                      type: string
            """;

    @Test
    void validatesSupportedSubsetWithInternalReference() {
        OpenApiResponseContract contract = OpenApiResponseContract.fromSpec(
                SPEC, new Operation("/pets", "GET", "listPets"), 200, "application/json");

        ContractCheck passing = contract.checkJson("[{\"id\":1,\"name\":\"mochi\"}]");
        assertTrue(passing.passed());

        ContractCheck failing = contract.checkJson("[{\"id\":\"x\"}]");
        assertFalse(failing.passed());
        assertEquals("required property is missing", failing.violations().get(0).message());
    }

    @Test
    void rejectsMismatchedStatusCodes() throws Exception {
        OpenApiResponseContract contract = OpenApiResponseContract.fromSpec(
                SPEC, new Operation("/pets", "GET", "listPets"), 200, "application/json");

        HttpServer server = server();
        server.createContext("/pets", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();

        ServiceHttpClient client = new ServiceHttpClient(baseUri(server, "/"), HttpPolicy.defaults());
        ServiceHttpResponse response = client.execute(ServiceHttpRequest.get("/pets").build());

        ContractCheck check = contract.check(response);
        assertFalse(check.passed());
        assertEquals("status", check.violations().get(0).location());
    }

    private HttpServer server() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servers.add(server);
        return server;
    }

    private static URI baseUri(HttpServer server, String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    @Test
    void rejectsOpenApi31AndExternalReferences() {
        assertThrows(OpenApiResponseContract.OpenApiContractException.class, () ->
                OpenApiResponseContract.fromSpec(
                        "openapi: 3.1.0\ninfo:\n  title: x\n  version: 1.0.0\npaths: {}",
                        new Operation("/pets", "GET", "listPets"),
                        200,
                        "application/json"));

        assertThrows(OpenApiResponseContract.OpenApiContractException.class, () ->
                OpenApiResponseContract.fromSpec("""
                        openapi: 3.0.3
                        info:
                          title: Pets
                          version: 1.0.0
                        paths:
                          /pets:
                            get:
                              responses:
                                '200':
                                  content:
                                    application/json:
                                      schema:
                                        $ref: 'https://example.test/schema.json'
                        """, new Operation("/pets", "GET", "listPets"), 200, "application/json"));
    }
}
