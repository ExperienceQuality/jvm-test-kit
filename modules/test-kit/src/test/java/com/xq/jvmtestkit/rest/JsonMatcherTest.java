package com.xq.jvmtestkit.rest;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonMatcherTest {
    @Test
    void matchesObjectSubsetsAndUnorderedArraySubsets() throws Exception {
        byte[] actual = "{\"items\":[{\"id\":2,\"name\":\"B\"},{\"id\":1}],\"trace\":\"x\"}"
                .getBytes(StandardCharsets.UTF_8);

        assertTrue(JsonMatcher.matches("{\"items\":[{\"id\":1},{\"id\":2}]}", actual));
        assertFalse(JsonMatcher.matches("{\"items\":[{\"id\":1},{\"id\":1}]}", actual));
        assertFalse(JsonMatcher.matches("{\"items\":[{\"id\":3}]}", actual));
    }

    @Test
    void matchesOverlappingArrayCandidatesIndependentOfExpectedOrder() throws Exception {
        byte[] actual = "[{\"id\":1,\"role\":\"admin\"},{\"id\":1}]"
                .getBytes(StandardCharsets.UTF_8);

        assertTrue(JsonMatcher.matches("[{\"id\":1},{\"id\":1,\"role\":\"admin\"}]", actual));
        assertTrue(JsonMatcher.matches("[{\"id\":1,\"role\":\"admin\"},{\"id\":1}]", actual));
    }

    @Test
    void requiresDistinctActualElementsForOverlappingExpectedElements() throws Exception {
        byte[] actual = "[{\"id\":1,\"role\":\"admin\"}]".getBytes(StandardCharsets.UTF_8);

        assertFalse(JsonMatcher.matches("[{\"id\":1},{\"id\":1,\"role\":\"admin\"}]", actual));
    }

    @Test
    void matchesNestedOverlappingArrayCandidates() throws Exception {
        byte[] actual = "{\"groups\":[{\"members\":[{\"id\":1,\"role\":\"admin\"},{\"id\":1}]}]}"
                .getBytes(StandardCharsets.UTF_8);

        assertTrue(JsonMatcher.matches(
                "{\"groups\":[{\"members\":[{\"id\":1},{\"id\":1,\"role\":\"admin\"}]}]}",
                actual
        ));
    }
}
