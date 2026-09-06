package com.xq.jvmtestkit.rest;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable values returned by one REST operation. */
public final class RestResponse {
    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final byte[] body;

    public RestResponse(int statusCode, Map<String, List<String>> headers, byte[] body) {
        this.statusCode = statusCode;
        this.headers = copyHeaders(headers);
        this.body = Arrays.copyOf(Objects.requireNonNull(body, "body"), body.length);
    }

    public int statusCode() {
        return statusCode;
    }

    public Map<String, List<String>> headers() {
        return headers;
    }

    public byte[] body() {
        return Arrays.copyOf(body, body.length);
    }

    public String bodyUtf8() {
        return bodyAs(StandardCharsets.UTF_8);
    }

    public String bodyAs(Charset charset) {
        return new String(body, Objects.requireNonNull(charset, "charset"));
    }

    public RestAssertions should() {
        return new DefaultRestAssertions(this);
    }

    private static Map<String, List<String>> copyHeaders(Map<String, List<String>> source) {
        Objects.requireNonNull(source, "headers");
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((name, values) -> copy.put(name, List.copyOf(values)));
        return Map.copyOf(copy);
    }
}
