package com.xq.jvmtestkit.rest;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestResponseTest {
    @Test
    void defensivelyCopiesValuesAndSupportsCharsetAccess() {
        byte[] source = "héllo".getBytes(StandardCharsets.UTF_8);
        RestResponse response = new RestResponse(200, Map.of("X-Trace", List.of("42")), source);
        source[0] = 'X';

        assertEquals("héllo", response.bodyUtf8());
        byte[] copy = response.body();
        copy[0] = 'Y';
        assertEquals("héllo", response.bodyAs(StandardCharsets.UTF_8));
        assertThrows(UnsupportedOperationException.class,
                () -> response.headers().put("X-New", List.of("value")));
    }

    @Test
    void fluentAssertionsCheckStatusAndLenientJson() {
        RestResponse response = new RestResponse(
                201,
                Map.of(),
                "{\"items\":[{\"id\":2},{\"id\":1}],\"extra\":true}".getBytes(StandardCharsets.UTF_8)
        );

        response.should().status(201).matchJson("{\"items\":[{\"id\":1}]}");
        assertThrows(AssertionError.class, () -> response.should().status(200));
        AssertionError mismatch = assertThrows(AssertionError.class,
                () -> response.should().matchJson("{\"secret\":\"must-not-appear\"}"));
        assertEquals(false, mismatch.getMessage().contains("must-not-appear"));
    }
}
