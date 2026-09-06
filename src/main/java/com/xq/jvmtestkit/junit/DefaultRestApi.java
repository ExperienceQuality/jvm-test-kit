package com.xq.jvmtestkit.junit;

import com.xq.jvmtestkit.rest.RestApi;
import com.xq.jvmtestkit.rest.RestApiConfig;
import com.xq.jvmtestkit.rest.RestRequest;
import com.xq.jvmtestkit.rest.RestResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class DefaultRestApi implements RestApi, AutoCloseable {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_REQUEST_BYTES = 2 * 1024 * 1024;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final URI baseUri;
    private final HttpClient client;
    private final AtomicBoolean closed = new AtomicBoolean();

    public DefaultRestApi(RestApiConfig config) {
        Objects.requireNonNull(config, "config");
        this.baseUri = config.baseUri();
        this.client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public RestResponse get(String path) {
        return get(path, RestRequest.empty());
    }

    @Override
    public RestResponse get(String path, RestRequest request) {
        if (Objects.requireNonNull(request, "request").body().length != 0) {
            throw new IllegalArgumentException("GET request must not include a body");
        }
        return execute("GET", path, request);
    }

    @Override
    public RestResponse post(String path, RestRequest request) {
        return execute("POST", path, request);
    }

    @Override
    public RestResponse put(String path, RestRequest request) {
        return execute("PUT", path, request);
    }

    @Override
    public void close() {
        closed.set(true);
    }

    private RestResponse execute(String method, String path, RestRequest request) {
        ensureOpen();
        Objects.requireNonNull(request, "request");
        byte[] body = request.body();
        if (body.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("REST request body exceeds the 2097152-byte limit");
        }
        URI uri = resolve(path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        request.headers().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));

        try {
            HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            return new RestResponse(
                    response.statusCode(),
                    response.headers().map(),
                    readBounded(response.body())
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("REST request was interrupted", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("REST request failed; remote details are redacted");
        }
    }

    private URI resolve(String path) {
        Objects.requireNonNull(path, "path");
        if (!path.startsWith("/") || path.startsWith("//") || path.indexOf('#') >= 0 || path.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("path must be a service-relative path without a fragment");
        }
        try {
            URI input = URI.create(path);
            if (input.isAbsolute() || input.getRawAuthority() != null || hasTraversal(input.getPath())) {
                throw new IllegalArgumentException("path must remain inside the configured service base URI");
            }
            URI resolved = baseUri.resolve(path.substring(1)).normalize();
            if (!sameOrigin(resolved) || !resolved.getPath().startsWith(baseUri.getPath())) {
                throw new IllegalArgumentException("path must remain inside the configured service base URI");
            }
            return resolved;
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("path must")) {
                throw exception;
            }
            throw new IllegalArgumentException("path is invalid", exception);
        }
    }

    private boolean sameOrigin(URI candidate) {
        return baseUri.getScheme().equalsIgnoreCase(candidate.getScheme())
                && baseUri.getHost().equalsIgnoreCase(candidate.getHost())
                && effectivePort(baseUri) == effectivePort(candidate);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static boolean hasTraversal(String path) {
        if (path == null) {
            return false;
        }
        for (String segment : path.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > MAX_RESPONSE_BYTES - total) {
                    throw new IOException("REST response body exceeds configured limit");
                }
                output.write(buffer, 0, count);
                total += count;
            }
            return output.toByteArray();
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("REST API is closed because its XQ test invocation finished");
        }
    }
}
