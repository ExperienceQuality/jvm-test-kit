package com.xq.jvmtestkit.gradle;

import org.gradle.api.GradleException;

import java.net.URI;
import java.time.Duration;

final class ServiceConfiguration {
    private static final Duration MAX_STARTUP_TIMEOUT = Duration.ofMinutes(15);

    private ServiceConfiguration() {
    }

    static String validateArtifactName(String artifactName) {
        if (artifactName == null || artifactName.isBlank()) {
            throw new GradleException("jvmTestKitService.artifactName must be a nonblank JAR basename, for example service.jar");
        }
        if (!artifactName.endsWith(".jar")
                || artifactName.contains("/")
                || artifactName.contains("\\")
                || artifactName.equals(".")
                || artifactName.equals("..")) {
            throw new GradleException("jvmTestKitService.artifactName must be a JAR basename without path segments, for example service.jar: " + artifactName);
        }
        return artifactName;
    }

    static URI validateHealthUrl(String healthUrl) {
        final URI uri;
        try {
            uri = URI.create(healthUrl);
        } catch (IllegalArgumentException exception) {
            throw new GradleException("jvmTestKitService.healthUrl must be an absolute HTTP(S) URL: " + healthUrl, exception);
        }
        if (!uri.isAbsolute()
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new GradleException("jvmTestKitService.healthUrl must be an absolute HTTP(S) URL without credentials, query, or fragment: " + healthUrl);
        }
        return uri;
    }

    static Duration validateStartupTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(MAX_STARTUP_TIMEOUT) > 0) {
            throw new GradleException("jvmTestKitService.startupTimeout must be greater than zero and no more than PT15M: " + timeout);
        }
        return timeout;
    }
}
