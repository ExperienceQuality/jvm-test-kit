package com.xq.jvmtestkit.junit;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;
import com.xq.jvmtestkit.config.ConfigurationManager;

final class XqConfiguration {
    private static final String RESOURCE = "xq.yaml";
    private static final String REST_BASE_URI = "xq.rest.base-uri";

    private final URI restBaseUri;
    private final StubSettings stub;

    private XqConfiguration(URI restBaseUri, StubSettings stub) {
        this.restBaseUri = restBaseUri;
        this.stub = stub;
    }

    static XqConfiguration load(ClassLoader loader) {
        if (loader == null) {
            loader = XqConfiguration.class.getClassLoader();
        }
        try {
            Enumeration<URL> resources = loader.getResources(RESOURCE);
            if (!resources.hasMoreElements()) {
                throw new IllegalStateException("Missing classpath configuration resource /xq.yaml");
            }
            URL resource = resources.nextElement();
            if (resources.hasMoreElements()) {
                throw new IllegalStateException("Multiple classpath configuration resources /xq.yaml found");
            }
            Properties properties = new Properties();
            try (InputStream input = resource.openStream()) {
                byte[] bytes = input.readAllBytes();
                String source = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                properties.load(new java.io.StringReader(source));
                String value = properties.getProperty(REST_BASE_URI);
                if (value == null || value.isBlank()) {
                    throw new IllegalStateException("Missing required property xq.rest.base-uri in /xq.yaml");
                }
                ConfigurationManager.StubSettings stub = ConfigurationManager.loadStubSettings(loader);
                return new XqConfiguration(normalize(URI.create(value)),
                        new StubSettings(stub.enabled(), stub.host(), stub.port(),
                                stub.resetBeforeScenario(), stub.isolateScenarios()));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read classpath configuration /xq.yaml", exception);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid property xq.rest.base-uri in /xq.yaml", exception);
        }
    }

    URI restBaseUri() {
        return restBaseUri;
    }

    StubSettings stub() {
        return stub;
    }

    record StubSettings(boolean enabled, String host, int port, boolean resetBeforeScenario,
                        boolean isolateScenarios) {
    }

    private static URI normalize(URI input) {
        if (!input.isAbsolute() || input.getHost() == null || input.getRawUserInfo() != null
                || input.getRawQuery() != null || input.getRawFragment() != null
                || !("http".equalsIgnoreCase(input.getScheme()) || "https".equalsIgnoreCase(input.getScheme()))) {
            throw new IllegalArgumentException(
                    "base URI must be an absolute HTTP(S) URI without credentials, query, or fragment");
        }
        URI normalized = input.normalize();
        String path = normalized.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        else if (!path.endsWith("/")) path += "/";
        return URI.create(normalized.getScheme() + "://" + normalized.getRawAuthority() + path);
    }
}
