# XQ JVM Test Kit

[![Verify](https://github.com/ExperienceQuality/jvm-test-kit/actions/workflows/verify.yml/badge.svg)](https://github.com/ExperienceQuality/jvm-test-kit/actions/workflows/verify.yml)

Minimal JVM service end-to-end test kit with JUnit Jupiter support.

The kit gives consumer repositories one small API for service-relative REST
calls, request setup, immutable responses, and JSON/status assertions. It is
intended for black-box service tests where the service is already running in
the test process, a test container, or a local integration environment.

## Template Use

This repository is also a template for new XQ JVM test-kit libraries. After
creating a repository from the template, update:

- `settings.gradle` with the new root project name.
- `build.gradle` with the new Maven `group`, package coordinates, POM name,
  description, URL, and SCM links.
- Java package names under `src/main/java` and `src/test/java` when the new kit
  should not publish `com.xq.jvmtestkit`.
- This README title, badge URLs, install coordinates, examples, and API
  descriptions so they describe the generated repository.
- `.github/workflows/*` release and verification commands if the new kit needs
  different compatibility, publishing, or consumer-fixture behavior.

## Install

Published artifacts use Maven coordinates:

```groovy
repositories {
    mavenCentral()
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/ExperienceQuality/jvm-test-kit")
        credentials {
            username = providers.environmentVariable("GITHUB_ACTOR").orNull
            password = providers.environmentVariable("GITHUB_TOKEN").orNull
        }
    }
}

dependencies {
    testImplementation "com.xq:jvm-test-kit:2.0.0"
    testRuntimeOnly "org.junit.platform:junit-platform-launcher"
}

test {
    useJUnitPlatform()
}
```

Use one released version in each consumer repository. CI needs
`packages: read` and a token that can read GitHub Packages.

## Quick Start

```java
import com.xq.jvmtestkit.junit.Xq;
import com.xq.jvmtestkit.junit.XqTest;
import com.xq.jvmtestkit.rest.RestApiConfig;
import com.xq.jvmtestkit.rest.RestRequest;
import com.xq.jvmtestkit.rest.RestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

@XqTest
class RoutineApiE2ETest {
    @BeforeEach
    void configureApi() {
        Xq.rest(RestApiConfig.at(URI.create("http://127.0.0.1:8080/api/")));
    }

    @Test
    void createsRoutine() {
        RestResponse response = Xq.rest().post(
                "/routines",
                RestRequest.builder()
                        .header("X-User-Id", "user-1")
                        .jsonBody(Map.of("name", "Strength A"))
                        .build()
        );

        response.should()
                .status(201)
                .matchJson(Map.of("name", "Strength A"));
    }
}
```

## Public API

Package `com.xq.jvmtestkit.junit`:

- `@XqTest` activates a fresh test context for every JUnit Jupiter test
  invocation and closes helpers when the invocation ends.
- `Xq.rest(RestApiConfig config)` creates and stores one REST helper for the
  active invocation.
- `Xq.rest()` returns the REST helper configured for the active invocation.

Package `com.xq.jvmtestkit.rest`:

- `RestApiConfig.at(URI baseUri)` validates and normalizes an absolute HTTP or
  HTTPS base URI. The URI must not include credentials, query, or fragment.
- `RestApi` supports `get(String path)`, `get(String path, RestRequest request)`,
  `post(String path, RestRequest request)`, and
  `put(String path, RestRequest request)`.
- `RestRequest.empty()` creates a request with no headers and no body.
- `RestRequest.builder()` creates immutable requests with repeated headers via
  `header(String, String)`, bulk headers via `headers(Map<String, String>)`, and
  JSON bodies via `jsonBody(Object)`.
- `RestResponse` exposes `statusCode()`, immutable response `headers()`,
  defensive-copy `body()`, `bodyUtf8()`, `bodyAs(Charset)`, and `should()`.
- `RestAssertions` supports fluent `status(int)`, `matchJson(String)`, and
  `matchJson(Object)` assertions.

## Supported Behavior

- JUnit lifecycle is per test invocation. A helper configured in one test is
  not reused by another test.
- `Xq.rest()` is only valid inside an active `@XqTest` invocation and after the
  test configured `Xq.rest(config)`.
- REST paths must be service-relative, start with `/`, and remain under the
  configured base URI.
- GET requests cannot include a body.
- Request and response bodies are capped at 2 MiB.
- HTTP calls use a 10 second timeout and do not follow redirects.
- Remote response bodies are redacted from kit-generated failure diagnostics.
- JSON matching is containment-based for objects and arrays. Expected object
  fields must exist in the actual JSON. Expected array elements may appear in
  any order inside the actual array.

## Non-goals

- The kit does not start or stop the service under test.
- The kit does not provide testcontainers, database fixtures, retries, polling,
  or service-specific payload builders.
- The kit does not expose the internal HTTP client, lifecycle extension, or JSON
  matcher as consumer APIs.

## Local Verification

```bash
./gradlew check
./gradlew publishToMavenLocal
./gradlew -p fixtures/clean-consumer test -PkitVersion=2.0.0-dev
```
