package com.xq.jvmtestkit.http;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable response values returned by {@link ServiceHttpClient}.
 */
public final class ServiceHttpResponse {
    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final byte[] body;

    ServiceHttpResponse(int statusCode, Map<String, List<String>> headers, byte[] body) {
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

    /**
     * Produces failure-safe metadata without exposing response body or header values.
     */
    public String sanitizedDiagnostics() {
        String headerNames = headers.keySet().stream()
                .map(ServiceHttpResponse::safeHeaderName)
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
        return "status=" + statusCode + "; headers=" + headerNames + "; body=<redacted:" + body.length + " bytes>";
    }

    private static String safeHeaderName(String name) {
        return isSensitiveHeader(name) ? name + "=<redacted>" : name;
    }

    private static boolean isSensitiveHeader(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.contains("authorization") || normalized.contains("cookie") || normalized.contains("token")
                || normalized.contains("secret") || normalized.contains("password") || normalized.contains("api-key");
    }

    private static Map<String, List<String>> copyHeaders(Map<String, List<String>> source) {
        Objects.requireNonNull(source, "headers");
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((name, values) -> copy.put(name, List.copyOf(values)));
        return Map.copyOf(copy);
    }
}
