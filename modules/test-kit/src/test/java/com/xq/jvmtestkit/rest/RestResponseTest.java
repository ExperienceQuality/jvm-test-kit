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

        response.should().hasStatus(201).containsJson("{\"items\":[{\"id\":1}]}");
        assertThrows(AssertionError.class, () -> response.should().hasStatus(200));
        AssertionError mismatch = assertThrows(AssertionError.class,
                () -> response.should().containsJson("{\"secret\":\"must-not-appear\"}"));
        assertEquals(false, mismatch.getMessage().contains("must-not-appear"));
    }

    @Test
    void supportsExactJsonAndJsonPathAssertions() {
        RestResponse response = new RestResponse(
                200,
                Map.of("Content-Type", List.of("application/json")),
                "{\"id\":7,\"items\":[{\"name\":\"A\"}],\"extra\":true}"
                        .getBytes(StandardCharsets.UTF_8)
        );

        response.should()
                .hasHeader("content-type", "application/json")
                .hasJsonPathValue("$.id", 7)
                .containsJson("{\"items\":[{\"name\":\"A\"}]}");
        assertThrows(AssertionError.class,
                () -> response.should().hasJsonBody("{\"id\":7}"));
    }

    @Test
    void distinguishesExactAndContainsAssertionsForObjectsArraysTypesAndNulls() {
        String body = "{\"customer\":{\"id\":\"c-1\",\"vip\":true},"
                + "\"items\":[{\"sku\":\"A\",\"quantity\":2},{\"sku\":\"B\",\"quantity\":1}],"
                + "\"optional\":null,\"active\":true,\"extra\":\"private-value\"}";
        RestResponse response = new RestResponse(200, Map.of(), body.getBytes(StandardCharsets.UTF_8));

        response.should()
                .containsJson("{\"customer\":{\"id\":\"c-1\"},\"items\":[{\"sku\":\"B\"}],\"optional\":null}")
                .containsJson("{\"active\":true}");
        assertThrows(AssertionError.class, () -> response.should()
                .containsJson("{\"customer\":{\"id\":7}}"));
        assertThrows(AssertionError.class, () -> response.should()
                .containsJson("{\"customer\":{\"missing\":\"value\"}}"));
        assertThrows(AssertionError.class, () -> response.should()
                .hasJsonBody("{\"customer\":{\"id\":\"c-1\",\"vip\":true},"
                        + "\"items\":[{\"sku\":\"A\",\"quantity\":2},{\"sku\":\"B\",\"quantity\":1}],"
                        + "\"optional\":null,\"active\":true}"));
        assertThrows(AssertionError.class, () -> response.should()
                .hasJsonBody("{\"customer\":{\"id\":\"c-1\",\"vip\":true},"
                        + "\"items\":[{\"sku\":\"B\",\"quantity\":1},{\"sku\":\"A\",\"quantity\":2}],"
                        + "\"optional\":null,\"active\":true,\"extra\":\"private-value\"}"));
        response.should().hasJsonBody(body);
    }

    @Test
    void distinguishesExactAndPartialPathAssertionsAndRedactsFailures() {
        RestResponse response = new RestResponse(200, Map.of(),
                ("{\"customer\":{\"id\":\"c-1\",\"name\":\"secret-name\",\"vip\":true},"
                        + "\"items\":[{\"sku\":\"A\",\"quantity\":2},{\"sku\":\"B\",\"quantity\":1}],"
                        + "\"extra\":\"secret-root\"}").getBytes(StandardCharsets.UTF_8));

        response.should()
                .containsJsonAtPath("$.customer", "{\"id\":\"c-1\"}")
                .containsJsonAtPath("$.items", "[{\"sku\":\"B\"}]")
                .hasJsonPathValue("$.customer", "{\"id\":\"c-1\",\"name\":\"secret-name\",\"vip\":true}");

        AssertionError exactMismatch = assertThrows(AssertionError.class,
                () -> response.should().hasJsonPathValue("$.customer", "{\"id\":\"c-1\"}"));
        AssertionError partialMismatch = assertThrows(AssertionError.class,
                () -> response.should().containsJsonAtPath("$.missing", "{\"id\":\"c-1\"}"));
        assertEquals(false, exactMismatch.getMessage().contains("secret-name"));
        assertEquals(false, exactMismatch.getMessage().contains("secret-root"));
        assertEquals(false, partialMismatch.getMessage().contains("secret-name"));
        assertEquals(false, partialMismatch.getMessage().contains("secret-root"));
    }

    @Test
    void containsArrayElementsRequireDistinctMatchesAndHandleOverlappingCandidatesInEitherOrder() {
        RestResponse duplicateMatches = jsonResponse(
                "[{\"id\":1,\"label\":\"private-a\"},{\"id\":1,\"label\":\"private-b\"}]");
        duplicateMatches.should().containsJson("[{\"id\":1},{\"id\":1}]");

        RestResponse overlappingCandidates = jsonResponse(
                "[{\"id\":1,\"kind\":\"specific\"},{\"id\":1,\"kind\":\"general\"}]");
        overlappingCandidates.should().containsJson(
                "[{\"id\":1},{\"id\":1,\"kind\":\"specific\"}]");

        RestResponse reversedOverlappingCandidates = jsonResponse(
                "[{\"id\":1,\"kind\":\"general\"},{\"id\":1,\"kind\":\"specific\"}]");
        reversedOverlappingCandidates.should().containsJson(
                "[{\"id\":1,\"kind\":\"specific\"},{\"id\":1}]");

        RestResponse insufficientMatches = jsonResponse("[{\"id\":1,\"label\":\"private-only\"}]");
        AssertionError failure = assertThrows(AssertionError.class,
                () -> insufficientMatches.should().containsJson("[{\"id\":1},{\"id\":1}]"));
        assertEquals(false, failure.getMessage().contains("private-only"));
    }

    private static RestResponse jsonResponse(String json) {
        return new RestResponse(200, Map.of(), json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsInvalidJsonAndMissingJsonPathsWithoutLeakingBody() {
        RestResponse response = new RestResponse(200, Map.of(),
                "{\"secret\":\"hidden\",\"value\":null}".getBytes(StandardCharsets.UTF_8));

        AssertionError jsonFailure = assertThrows(AssertionError.class,
                () -> response.should().hasJsonBody("not-json"));
        AssertionError pathFailure = assertThrows(AssertionError.class,
                () -> response.should().hasJsonPathValue("$.missing", "value"));
        assertEquals(false, jsonFailure.getMessage().contains("hidden"));
        assertEquals(false, pathFailure.getMessage().contains("hidden"));
    }

    @Test
    void supportsRepeatedHeadersAndEmptyBody() {
        RestResponse response = new RestResponse(204,
                Map.of("Set-Cookie", List.of("a=1", "b=2")), new byte[0]);
        response.should().hasHeader("set-cookie", "b=2").hasEmptyBody();
    }
}
