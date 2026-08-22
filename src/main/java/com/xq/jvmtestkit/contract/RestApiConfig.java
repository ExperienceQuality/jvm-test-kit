package com.xq.jvmtestkit.contract;

import com.xq.jvmtestkit.http.HttpPolicy;

import java.net.URI;
import java.util.Objects;

/**
 * Configuration for one named REST API under test.
 */
public final class RestApiConfig {
    private final URI serviceBaseUri;
    private final HttpPolicy httpPolicy;

    public RestApiConfig(URI serviceBaseUri, HttpPolicy httpPolicy) {
        this.serviceBaseUri = Objects.requireNonNull(serviceBaseUri, "serviceBaseUri");
        this.httpPolicy = Objects.requireNonNull(httpPolicy, "httpPolicy");
    }

    public static RestApiConfig at(URI serviceBaseUri) {
        return new RestApiConfig(serviceBaseUri, HttpPolicy.defaults());
    }

    public URI serviceBaseUri() {
        return serviceBaseUri;
    }

    public HttpPolicy httpPolicy() {
        return httpPolicy;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RestApiConfig that)) {
            return false;
        }
        return serviceBaseUri.equals(that.serviceBaseUri)
                && httpPolicy.requestTimeout().equals(that.httpPolicy.requestTimeout())
                && httpPolicy.maxRedirects() == that.httpPolicy.maxRedirects()
                && httpPolicy.maxRequestBodyBytes() == that.httpPolicy.maxRequestBodyBytes()
                && httpPolicy.maxResponseBodyBytes() == that.httpPolicy.maxResponseBodyBytes();
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                serviceBaseUri,
                httpPolicy.requestTimeout(),
                httpPolicy.maxRedirects(),
                httpPolicy.maxRequestBodyBytes(),
                httpPolicy.maxResponseBodyBytes()
        );
    }
}
