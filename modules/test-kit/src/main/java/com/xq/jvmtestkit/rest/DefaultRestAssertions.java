package com.xq.jvmtestkit.rest;

import java.io.IOException;
import java.util.Objects;

final class DefaultRestAssertions implements RestAssertions {
    private final RestResponse response;

    DefaultRestAssertions(RestResponse response) {
        this.response = Objects.requireNonNull(response, "response");
    }

    @Override
    public RestAssertions status(int expected) {
        if (response.statusCode() != expected) {
            throw new AssertionError("REST status mismatch: expected=" + expected
                    + "; actual=" + response.statusCode() + "; " + diagnostics());
        }
        return this;
    }

    @Override
    public RestAssertions matchJson(String expectedJson) {
        Objects.requireNonNull(expectedJson, "expectedJson");
        try {
            if (!JsonMatcher.matches(expectedJson, response.body())) {
                throw new AssertionError("REST JSON did not contain the expected structure; " + diagnostics());
            }
            return this;
        } catch (IOException exception) {
            throw new AssertionError("REST JSON assertion received invalid JSON; " + diagnostics(), exception);
        }
    }

    @Override
    public RestAssertions matchJson(Object expectedValue) {
        return matchJson(JsonMatcher.serialize(expectedValue));
    }

    private String diagnostics() {
        return "status=" + response.statusCode() + "; body=<redacted:" + response.body().length + " bytes>";
    }
}
