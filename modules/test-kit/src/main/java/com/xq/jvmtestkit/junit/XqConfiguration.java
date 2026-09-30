package com.xq.jvmtestkit.junit;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;

final class XqConfiguration {
    private static final String RESOURCE = "xq.properties";
    private static final String REST_BASE_URI = "xq.rest.base-uri";

    private final URI restBaseUri;

    private XqConfiguration(URI restBaseUri) {
        this.restBaseUri = restBaseUri;
    }

    static XqConfiguration load(ClassLoader loader) {
        if (loader == null) {
            loader = XqConfiguration.class.getClassLoader();
        }
        try {
            Enumeration<URL> resources = loader.getResources(RESOURCE);
            if (!resources.hasMoreElements()) {
                throw new IllegalStateException("Missing classpath configuration resource /xq.properties");
            }
            URL resource = resources.nextElement();
            if (resources.hasMoreElements()) {
                throw new IllegalStateException("Multiple classpath configuration resources /xq.properties found");
            }
            Properties properties = new Properties();
            try (InputStream input = resource.openStream()) {
                properties.load(input);
            }
            String value = properties.getProperty(REST_BASE_URI);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Missing required property xq.rest.base-uri in /xq.properties");
            }
            return new XqConfiguration(normalize(URI.create(value)));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read classpath configuration /xq.properties", exception);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid property xq.rest.base-uri in /xq.properties", exception);
        }
    }

    URI restBaseUri() {
        return restBaseUri;
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
