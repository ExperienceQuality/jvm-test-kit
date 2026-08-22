package com.xq.jvmtestkit.dto;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RestResponse(int statusCode, Map<String, List<String>> headers, byte[] body) {
    public RestResponse {
        headers = copyHeaders(headers);
        body = Arrays.copyOf(Objects.requireNonNull(body, "body"), body.length);
    }

    @Override
    public byte[] body() {
        return Arrays.copyOf(body, body.length);
    }

    private static Map<String, List<String>> copyHeaders(Map<String, List<String>> source) {
        Objects.requireNonNull(source, "headers");
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((name, values) -> copy.put(name, List.copyOf(values)));
        return Map.copyOf(copy);
    }
}
