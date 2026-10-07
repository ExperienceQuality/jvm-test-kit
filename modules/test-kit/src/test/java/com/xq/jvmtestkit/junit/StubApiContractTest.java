package com.xq.jvmtestkit.junit;

import com.xq.jvmtestkit.stub.StubApi;
import com.xq.jvmtestkit.stub.StubRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StubApiContractTest {
    private StubApi stub;

    @AfterEach
    void stop() {
        if (stub != null) stub.close();
        StubRuntime.shutdown();
    }

    @Test
    void startsOnLoopbackAndSupportsNativeMappingsAndVerification() throws Exception {
        String scenarioId = UUID.randomUUID().toString();
        stub = StubRuntime.open(scenarioId, "127.0.0.1", 0, true, true).start();
        stub.stubFor(get(urlPathEqualTo("/logs"))
                .withQueryParam("exerciseName", equalTo("Squat"))
                .willReturn(aResponse().withStatus(200).withBody("[{\"id\":11}]")));

        URI uri = stub.baseUri().resolve("logs?exerciseName=Squat");
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri).header(StubApi.TEST_ID_HEADER, scenarioId).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("[{\"id\":11}]", response.body());
        stub.verify(getRequestedFor(urlPathEqualTo("/logs"))
                .withQueryParam("exerciseName", equalTo("Squat")));
    }

    @Test
    void resetIsScopedToTheScenario() throws Exception {
        String first = currentScenarioId();
        stub = StubRuntime.open(first, "127.0.0.1", 0, false, true).start();
        stub.stubFor(get(urlPathEqualTo("/first"))
                .willReturn(aResponse().withStatus(200)));
        HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(stub.baseUri().resolve("first"))
                        .header(StubApi.TEST_ID_HEADER, first).GET().build(),
                HttpResponse.BodyHandlers.discarding());

        StubApi second = StubRuntime.open(UUID.randomUUID().toString(), "127.0.0.1", 0, false, true).start();
        second.reset();
        stub.verify(1, getRequestedFor(urlPathEqualTo("/first")));
        second.close();
    }

    private static String currentScenarioId() {
        return UUID.randomUUID().toString();
    }
}
