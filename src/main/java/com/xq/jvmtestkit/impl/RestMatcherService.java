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
        return null;
    }

    @Override
    public RestApi.RestMatcher equalToJsonSchema(String jsonSchema) {
        return null;
    }

    @Override
    public RestApi.RestMatcher match(String json) {
        return null;
    }

    @Override
    public RestApi.RestMatcher match(Object json) {
        return null;
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
}
