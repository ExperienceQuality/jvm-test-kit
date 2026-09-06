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
}
