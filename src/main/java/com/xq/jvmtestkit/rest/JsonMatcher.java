package com.xq.jvmtestkit.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

final class JsonMatcher {
    private static final ObjectMapper JSON = new ObjectMapper();

    private JsonMatcher() {
    }

    static boolean matches(String expectedJson, byte[] actualJson) throws IOException {
        Objects.requireNonNull(expectedJson, "expectedJson");
        Objects.requireNonNull(actualJson, "actualJson");
        return matches(JSON.readTree(expectedJson), JSON.readTree(actualJson));
    }

    static String serialize(Object expectedValue) {
        Objects.requireNonNull(expectedValue, "expectedValue");
        try {
            return JSON.writeValueAsString(expectedValue);
        } catch (JsonProcessingException exception) {
            throw new AssertionError("Expected JSON value could not be serialized", exception);
        }
    }

    private static boolean matches(JsonNode expected, JsonNode actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        if (expected.isObject()) {
            if (!actual.isObject()) {
                return false;
            }
            var fields = expected.properties().iterator();
            while (fields.hasNext()) {
                var field = fields.next();
                if (!actual.has(field.getKey()) || !matches(field.getValue(), actual.get(field.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        if (expected.isArray()) {
            if (!actual.isArray() || expected.size() > actual.size()) {
                return false;
            }
            boolean[] used = new boolean[actual.size()];
            for (JsonNode expectedElement : expected) {
                boolean found = false;
                for (int index = 0; index < actual.size(); index++) {
                    if (!used[index] && matches(expectedElement, actual.get(index))) {
                        used[index] = true;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }
        return expected.equals(actual);
    }
}
