package com.xq.jvmtestkit.impl;

import com.xq.jvmtestkit.contract.RestApi;
import com.xq.jvmtestkit.dto.RestResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;

public final class RestMatcherService implements RestApi.RestMatcher {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final RestResponse restResponse;

    public RestMatcherService(RestResponse restResponse) {
        this.restResponse = Objects.requireNonNull(restResponse, "restResponse");
    }

    @Override
    public RestApi.RestMatcher status(int expectedStatus) {
        if (restResponse.statusCode() != expectedStatus) {
            throw new AssertionError("REST status mismatch: expected=" + expectedStatus
                    + "; " + sanitizedDiagnostics());
        }
        return this;
    }

    @Override
    public RestApi.RestMatcher equalToJson(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonNode expected = JSON.readTree(json);
            JsonNode actual = JSON.readTree(restResponse.body());
            if (!expected.equals(actual)) {
                throw new AssertionError(JsonDiffFormatter.format(pretty(expected), pretty(actual))
                        + "\n" + sanitizedDiagnostics());
            }
        } catch (IOException exception) {
            throw new AssertionError("REST JSON assertion received invalid JSON", exception);
        }
        return this;
    }

    @Override
    public RestApi.RestMatcher equalToJson(Object json) {
        return equalToJson(serialize(json));
    }

    @Override
    public RestApi.RestMatcher equalToJsonSchema(String jsonSchema) {
        return null;
    }

    @Override
    public RestApi.RestMatcher match(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonNode expected = JSON.readTree(json);
            JsonNode actual = JSON.readTree(restResponse.body());
            if (!matches(expected, actual)) {
                throw new AssertionError(JsonDiffFormatter.format(pretty(expected), pretty(actual))
                        + "\n" + sanitizedDiagnostics());
            }
            return this;
        } catch (IOException exception) {
            throw new AssertionError("REST JSON match received invalid JSON", exception);
        }
    }

    @Override
    public RestApi.RestMatcher match(Object json) {
        return match(serialize(json));
    }

    private String sanitizedDiagnostics() {
        return "status=" + restResponse.statusCode() + "; body=<redacted:" + restResponse.body().length + " bytes>";
    }

    private static String pretty(JsonNode value) {
        try {
            return JSON.writer().with(INDENT_OUTPUT).writeValueAsString(value);
        } catch (IOException exception) {
            throw new AssertionError("REST JSON assertion could not format JSON", exception);
        }
    }

    private static String serialize(Object value) {
        Objects.requireNonNull(value, "json");
        try {
            return JSON.writeValueAsString(value);
        } catch (IOException exception) {
            throw new AssertionError("REST JSON assertion could not serialize expected value", exception);
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
            var fields = expected.fields();
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
