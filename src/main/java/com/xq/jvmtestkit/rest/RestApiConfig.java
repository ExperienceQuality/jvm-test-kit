package com.xq.jvmtestkit.rest;

import java.net.URI;
import java.util.Objects;

/** Configuration for one service API under test. */
public record RestApiConfig(URI baseUri) {
    public RestApiConfig {
        baseUri = normalize(baseUri);
    }

    public static RestApiConfig at(URI baseUri) {
        return new RestApiConfig(baseUri);
    }

    private static URI normalize(URI input) {
        Objects.requireNonNull(input, "baseUri");
        if (!input.isAbsolute() || input.getHost() == null || input.getRawUserInfo() != null
                || input.getRawQuery() != null || input.getRawFragment() != null
                || !("http".equalsIgnoreCase(input.getScheme()) || "https".equalsIgnoreCase(input.getScheme()))) {
            throw new IllegalArgumentException(
                    "baseUri must be an absolute HTTP(S) URI without credentials, query, or fragment"
            );
        }
        URI normalized = input.normalize();
        String path = normalized.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        } else if (!path.endsWith("/")) {
            path += "/";
        }
        return URI.create(normalized.getScheme() + "://" + normalized.getRawAuthority() + path);
    }
}
