package com.xq.jvmtestkit.http;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable HTTP request addressed relative to a {@link ServiceHttpClient}'s service base URI.
 */
public final class ServiceHttpRequest {
    private final String method;
    private final String pathAndQuery;
    private final Map<String, List<String>> headers;
    private final byte[] body;

    private ServiceHttpRequest(Builder builder) {
        this.method = builder.method;
        this.pathAndQuery = builder.pathAndQuery;
        this.headers = copyHeaders(builder.headers);
        this.body = Arrays.copyOf(builder.body, builder.body.length);
    }

    public static Builder builder(String method, String pathAndQuery) {
        return new Builder(method, pathAndQuery);
    }

    public static Builder get(String pathAndQuery) {
        return builder("GET", pathAndQuery);
    }

    public static Builder post(String pathAndQuery) {
        return builder("POST", pathAndQuery);
    }

    public String method() {
        return method;
    }

    public String pathAndQuery() {
        return pathAndQuery;
    }

    public Map<String, List<String>> headers() {
        return headers;
    }

    public byte[] body() {
        return Arrays.copyOf(body, body.length);
    }

    public static final class Builder {
        private final String method;
        private final String pathAndQuery;
        private final Map<String, List<String>> headers = new LinkedHashMap<>();
        private byte[] body = new byte[0];

        private Builder(String method, String pathAndQuery) {
            this.method = requireMethod(method);
            this.pathAndQuery = requireRelativePath(pathAndQuery);
        }

        public Builder header(String name, String value) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            if (name.isBlank() || containsLineBreak(name) || containsLineBreak(value)) {
                throw new IllegalArgumentException("HTTP header name and value must not contain line breaks");
            }
            headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
            return this;
        }

        public Builder body(byte[] body) {
            this.body = Arrays.copyOf(Objects.requireNonNull(body, "body"), body.length);
            return this;
        }

        public Builder bodyUtf8(String body) {
            return body(Objects.requireNonNull(body, "body").getBytes(StandardCharsets.UTF_8));
        }

        public ServiceHttpRequest build() {
            return new ServiceHttpRequest(this);
        }

        private static String requireMethod(String method) {
            Objects.requireNonNull(method, "method");
            if (method.isBlank() || !method.chars().allMatch(character ->
                    character > ' ' && character < 127 && character != ':' && character != '\r' && character != '\n')) {
                throw new IllegalArgumentException("HTTP method is invalid");
            }
            return method;
        }

        private static String requireRelativePath(String pathAndQuery) {
            Objects.requireNonNull(pathAndQuery, "pathAndQuery");
            if (!pathAndQuery.startsWith("/") || pathAndQuery.startsWith("//")
                    || pathAndQuery.indexOf('#') >= 0 || pathAndQuery.indexOf('\\') >= 0 || hasTraversal(pathAndQuery)) {
                throw new IllegalArgumentException("pathAndQuery must be a service-relative path without a fragment");
            }
            return pathAndQuery;
        }

        private static boolean containsLineBreak(String value) {
            return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
        }

        private static boolean hasTraversal(String value) {
            String path = java.net.URI.create(value).getPath();
            for (String segment : path.split("/")) {
                if (".".equals(segment) || "..".equals(segment)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static Map<String, List<String>> copyHeaders(Map<String, List<String>> source) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((name, values) -> copy.put(name, List.copyOf(values)));
        return Map.copyOf(copy);
    }
}
