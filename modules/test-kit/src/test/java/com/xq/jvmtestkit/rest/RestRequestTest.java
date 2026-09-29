package com.xq.jvmtestkit.rest;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestRequestTest {
    @Test
    void buildsImmutableHeadersAndJsonBodyWithDefaultContentType() {
        RestRequest request = RestRequest.builder()
                .headers(Map.of("X-User-Id", "user-1"))
                .header("X-Trace", "first")
                .header("X-Trace", "second")
                .jsonBody(Map.of("name", "Strength A"))
                .build();

        assertEquals("{\"name\":\"Strength A\"}", new String(request.body(), StandardCharsets.UTF_8));
        assertEquals(2, request.headers().get("X-Trace").size());
        assertEquals("application/json", request.headers().get("Content-Type").getFirst());
        assertThrows(UnsupportedOperationException.class,
                () -> request.headers().put("Another", java.util.List.of("value")));
    }

    @Test
    void respectsExplicitContentTypeAndRejectsUnsafeHeadersAndNullJson() {
        RestRequest request = RestRequest.builder()
                .header("content-type", "application/problem+json")
                .jsonBody(Map.of("code", "NOPE"))
                .build();

        assertEquals(1, request.headers().size());
        assertThrows(IllegalArgumentException.class,
                () -> RestRequest.builder().header("X-Test\nInjected", "value"));
        assertThrows(NullPointerException.class, () -> RestRequest.builder().jsonBody(null));
    }
}
