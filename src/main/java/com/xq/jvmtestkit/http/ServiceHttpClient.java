package com.xq.jvmtestkit.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Executes bounded requests against one configured service base URI.
 */
public final class ServiceHttpClient {
    private final URI serviceBaseUri;
    private final HttpPolicy policy;
    private final HttpClient transport;

    public ServiceHttpClient(URI serviceBaseUri, HttpPolicy policy) {
        this.serviceBaseUri = normalizeBaseUri(serviceBaseUri);
        this.policy = Objects.requireNonNull(policy, "policy");
        this.transport = HttpClient.newBuilder()
                .connectTimeout(policy.requestTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public URI serviceBaseUri() {
        return serviceBaseUri;
    }

    public HttpPolicy policy() {
        return policy;
    }

    public ServiceHttpResponse execute(ServiceHttpRequest request) throws IOException, InterruptedException {
        Objects.requireNonNull(request, "request");
        if (request.body().length > policy.maxRequestBodyBytes()) {
            throw new IOException("Service HTTP request body exceeds configured limit");
        }

        URI requestUri = resolveRequest(request.pathAndQuery());
        int redirects = 0;
        while (true) {
            HttpResponse<InputStream> response = send(request, requestUri);
            if (!isRedirect(response.statusCode())) {
                return new ServiceHttpResponse(
                        response.statusCode(), response.headers().map(), readBoundedBody(response.body(), policy.maxResponseBodyBytes()));
            }

            if (redirects >= policy.maxRedirects()) {
                closeQuietly(response.body());
                throw new IOException("Service HTTP redirect limit exceeded");
            }

            String location = firstHeader(response.headers().map(), "location");
            closeQuietly(response.body());
            if (location == null) {
                throw new IOException("Service HTTP redirect response did not include a location");
            }
            requestUri = resolveRedirect(requestUri, location);
            redirects++;
        }
    }

    private HttpResponse<InputStream> send(ServiceHttpRequest request, URI requestUri) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(requestUri)
                .timeout(policy.requestTimeout())
                .method(request.method(), HttpRequest.BodyPublishers.ofByteArray(request.body()));
        request.headers().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        try {
            return transport.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new IOException("Service HTTP request timed out", exception);
        }
    }

    private URI resolveRequest(String pathAndQuery) throws IOException {
        try {
            URI input = URI.create(pathAndQuery);
            if (input.isAbsolute() || input.getRawAuthority() != null || hasTraversal(input.getPath())) {
                throw new IOException("Service HTTP request path is outside the configured service base URI");
            }
            URI resolved = serviceBaseUri.resolve(pathAndQuery.substring(1)).normalize();
            if (!isWithinServiceBase(resolved)) {
                throw new IOException("Service HTTP request path is outside the configured service base URI");
            }
            return resolved;
        } catch (IllegalArgumentException exception) {
            throw new IOException("Service HTTP request path is invalid", exception);
        }
    }

    private URI resolveRedirect(URI currentRequestUri, String location) throws IOException {
        try {
            URI redirect = currentRequestUri.resolve(URI.create(location)).normalize();
            if (!isWithinServiceBase(redirect)) {
                throw new IOException("Service HTTP redirect is outside the configured service base URI");
            }
            return redirect;
        } catch (IllegalArgumentException exception) {
            throw new IOException("Service HTTP redirect location is invalid", exception);
        }
    }

    private boolean isWithinServiceBase(URI candidate) {
        return serviceBaseUri.getScheme().equalsIgnoreCase(candidate.getScheme())
                && serviceBaseUri.getHost().equalsIgnoreCase(candidate.getHost())
                && effectivePort(serviceBaseUri) == effectivePort(candidate)
                && candidate.getPath().startsWith(serviceBaseUri.getPath());
    }

    private static URI normalizeBaseUri(URI baseUri) {
        Objects.requireNonNull(baseUri, "serviceBaseUri");
        if (!baseUri.isAbsolute() || baseUri.getHost() == null || baseUri.getRawUserInfo() != null
                || baseUri.getRawQuery() != null || baseUri.getRawFragment() != null
                || !("http".equalsIgnoreCase(baseUri.getScheme()) || "https".equalsIgnoreCase(baseUri.getScheme()))) {
            throw new IllegalArgumentException("serviceBaseUri must be an absolute HTTP(S) URI without credentials, query, or fragment");
        }
        URI normalized = baseUri.normalize();
        String path = normalized.getPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        return URI.create(normalized.getScheme() + "://" + normalized.getRawAuthority() + path);
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

    private static boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
    }

    private static String firstHeader(Map<String, List<String>> headers, String expectedName) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(expectedName))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst()
                .orElse(null);
    }

    private static byte[] readBoundedBody(InputStream input, int maximumBytes) throws IOException {
        try (input) {
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximumBytes, 8_192));
            byte[] buffer = new byte[8_192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > maximumBytes - total) {
                    throw new IOException("Service HTTP response body exceeds configured limit");
                }
                output.write(buffer, 0, count);
                total += count;
            }
            return output.toByteArray();
        }
    }

    private static void closeQuietly(InputStream body) {
        try {
            body.close();
        } catch (IOException ignored) {
            // The redirect failure takes precedence and contains no remote data.
        }
    }
}
