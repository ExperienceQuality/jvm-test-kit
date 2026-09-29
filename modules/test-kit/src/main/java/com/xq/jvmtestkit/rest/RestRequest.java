package com.xq.jvmtestkit.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable headers and optional JSON body for one REST operation. */
public final class RestRequest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final RestRequest EMPTY = new Builder().build();

    private final Map<String, List<String>> headers;
    private final byte[] body;

    private RestRequest(Builder builder) {
        this.headers = copyHeaders(builder.headers);
        this.body = Arrays.copyOf(builder.body, builder.body.length);
    }

    public static RestRequest empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, List<String>> headers() {
        return headers;
    }

    public byte[] body() {
        return Arrays.copyOf(body, body.length);
    }

    public static final class Builder {
        private final Map<String, List<String>> headers = new LinkedHashMap<>();
        private byte[] body = new byte[0];
        private boolean hasJsonBody;

        public Builder header(String name, String value) {
            Objects.requireNonNull(name, "header name");
            Objects.requireNonNull(value, "header value");
            if (name.isBlank() || containsLineBreak(name) || containsLineBreak(value)) {
                throw new IllegalArgumentException("HTTP header name and value must not be blank or contain line breaks");
            }
            headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
            return this;
        }

        public Builder headers(Map<String, String> values) {
            Objects.requireNonNull(values, "headers").forEach(this::header);
            return this;
        }

        public Builder jsonBody(Object value) {
            Objects.requireNonNull(value, "JSON body");
            try {
                body = JSON.writeValueAsBytes(value);
                hasJsonBody = true;
                return this;
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException("JSON body could not be serialized", exception);
            }
        }

        public RestRequest build() {
            if (hasJsonBody && headers.keySet().stream().noneMatch(name -> name.equalsIgnoreCase("Content-Type"))) {
                header("Content-Type", "application/json");
            }
            return new RestRequest(this);
        }

        private static boolean containsLineBreak(String value) {
            return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
        }
    }

    private static Map<String, List<String>> copyHeaders(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((name, values) -> copy.put(name, List.copyOf(values)));
        return Map.copyOf(copy);
    }
}
