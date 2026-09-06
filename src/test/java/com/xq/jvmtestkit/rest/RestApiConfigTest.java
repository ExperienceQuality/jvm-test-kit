package com.xq.jvmtestkit.rest;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestApiConfigTest {
    @Test
    void normalizesValidHttpBaseUris() {
        assertEquals(
                URI.create("https://example.test/api/"),
                RestApiConfig.at(URI.create("https://example.test/api")).baseUri()
        );
    }

    @Test
    void rejectsCredentialsQueriesFragmentsAndUnsupportedSchemes() {
        assertThrows(IllegalArgumentException.class,
                () -> RestApiConfig.at(URI.create("https://user:secret@example.test/")));
        assertThrows(IllegalArgumentException.class,
                () -> RestApiConfig.at(URI.create("https://example.test/?token=secret")));
        assertThrows(IllegalArgumentException.class,
                () -> RestApiConfig.at(URI.create("https://example.test/#fragment")));
        assertThrows(IllegalArgumentException.class,
                () -> RestApiConfig.at(URI.create("file:///tmp/service")));
    }
}
