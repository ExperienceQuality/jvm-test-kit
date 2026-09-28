package com.xq.jvmtestkit.gradle

import org.gradle.api.GradleException

import java.time.Duration

final class ServiceConfiguration {
    private static final Duration MAX_STARTUP_TIMEOUT = Duration.ofMinutes(15)

    private ServiceConfiguration() {
    }

    static String validateArtifactName(String artifactName) {
        if (artifactName == null || artifactName.isBlank()) {
            throw new GradleException('jvmTestKitService.artifactName must be a nonblank JAR basename, for example service.jar')
        }
        if (!artifactName.endsWith('.jar')
                || artifactName.contains('/')
                || artifactName.contains('\\')
                || artifactName == '.'
                || artifactName == '..') {
            throw new GradleException('jvmTestKitService.artifactName must be a JAR basename without path segments, for example service.jar: ' + artifactName)
        }
        artifactName
    }

    static URI validateHealthUrl(String healthUrl) {
        URI uri
        try {
            uri = URI.create(healthUrl)
        } catch (IllegalArgumentException exception) {
            throw new GradleException('jvmTestKitService.healthUrl must be an absolute HTTP(S) URL: ' + healthUrl, exception)
        }
        if (!uri.absolute
                || !(uri.scheme.equalsIgnoreCase('http') || uri.scheme.equalsIgnoreCase('https'))
                || uri.host == null
                || uri.userInfo != null
                || uri.query != null
                || uri.fragment != null) {
            throw new GradleException('jvmTestKitService.healthUrl must be an absolute HTTP(S) URL without credentials, query, or fragment: ' + healthUrl)
        }
        uri
    }

    static Duration validateStartupTimeout(Duration timeout) {
        if (timeout == null || timeout.zero || timeout.negative || timeout > MAX_STARTUP_TIMEOUT) {
            throw new GradleException('jvmTestKitService.startupTimeout must be greater than zero and no more than PT15M: ' + timeout)
        }
        timeout
    }
}
