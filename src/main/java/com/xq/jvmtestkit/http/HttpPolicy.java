package com.xq.jvmtestkit.http;

import java.time.Duration;
import java.util.Objects;

/**
 * Explicit resource limits for calls made through {@link ServiceHttpClient}.
 */
public final class HttpPolicy {
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int DEFAULT_MAX_REDIRECTS = 5;
    private static final int DEFAULT_MAX_REQUEST_BODY_BYTES = 1_048_576;
    private static final int DEFAULT_MAX_RESPONSE_BODY_BYTES = 1_048_576;
    private static final int ABSOLUTE_MAX_REDIRECTS = 20;
    private static final int ABSOLUTE_MAX_BODY_BYTES = 16 * 1_048_576;

    private final Duration requestTimeout;
    private final int maxRedirects;
    private final int maxRequestBodyBytes;
    private final int maxResponseBodyBytes;

    private HttpPolicy(Builder builder) {
        this.requestTimeout = builder.requestTimeout;
        this.maxRedirects = builder.maxRedirects;
        this.maxRequestBodyBytes = builder.maxRequestBodyBytes;
        this.maxResponseBodyBytes = builder.maxResponseBodyBytes;
    }

    public static HttpPolicy defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    public int maxRedirects() {
        return maxRedirects;
    }

    public int maxRequestBodyBytes() {
        return maxRequestBodyBytes;
    }

    public int maxResponseBodyBytes() {
        return maxResponseBodyBytes;
    }

    public static final class Builder {
        private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        private int maxRedirects = DEFAULT_MAX_REDIRECTS;
        private int maxRequestBodyBytes = DEFAULT_MAX_REQUEST_BODY_BYTES;
        private int maxResponseBodyBytes = DEFAULT_MAX_RESPONSE_BODY_BYTES;

        private Builder() {
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requirePositive(requestTimeout, "requestTimeout");
            return this;
        }

        public Builder maxRedirects(int maxRedirects) {
            if (maxRedirects < 0 || maxRedirects > ABSOLUTE_MAX_REDIRECTS) {
                throw new IllegalArgumentException("maxRedirects must be between 0 and " + ABSOLUTE_MAX_REDIRECTS);
            }
            this.maxRedirects = maxRedirects;
            return this;
        }

        public Builder maxRequestBodyBytes(int maxRequestBodyBytes) {
            this.maxRequestBodyBytes = requireBodyLimit(maxRequestBodyBytes, "maxRequestBodyBytes");
            return this;
        }

        public Builder maxResponseBodyBytes(int maxResponseBodyBytes) {
            this.maxResponseBodyBytes = requireBodyLimit(maxResponseBodyBytes, "maxResponseBodyBytes");
            return this;
        }

        public HttpPolicy build() {
            return new HttpPolicy(this);
        }

        private static Duration requirePositive(Duration value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        }

        private static int requireBodyLimit(int value, String name) {
            if (value < 0 || value > ABSOLUTE_MAX_BODY_BYTES) {
                throw new IllegalArgumentException(name + " must be between 0 and " + ABSOLUTE_MAX_BODY_BYTES);
            }
            return value;
        }
    }
}
