# JVM Test Kit v2 API

`com.xq:jvm-test-kit` is a small Java 21 library for HTTP-based service E2E
tests with JUnit Jupiter. Version 2 intentionally replaces the earlier API.

## Configure one API per test invocation

```java
@XqTest
class RoutineE2ETest {
    @BeforeEach
    void configure() {
        Xq.rest(RestApiConfig.at(URI.create("http://localhost:8080/")));
    }
}
```

`@XqTest` creates and closes an isolated context for every invocation.
`Xq.rest()` is valid only after configuration and on the active test thread.

## Send requests

```java
RestResponse created = Xq.rest().post(
        "/api/v1/routines",
        RestRequest.builder()
                .header("X-User-Id", userId)
                .jsonBody(Map.of("name", "Strength A"))
                .build()
);

created.should()
        .status(201)
        .matchJson("{\"name\":\"Strength A\"}");
```

`RestApi` supports GET, POST, and PUT. Paths are service-relative and cannot
escape the configured base path. GET rejects a body. JSON bodies are encoded as
UTF-8 and receive `Content-Type: application/json` unless one was supplied.

`RestResponse` exposes `statusCode`, immutable `headers`, defensive-copy `body`,
`bodyUtf8`, `bodyAs(Charset)`, and `should`.

## JSON matching

`matchJson` requires expected object fields and expected array elements to be
present. Object key order and array order are ignored, and extra actual fields
and array elements are allowed. Duplicate expected array elements require
distinct actual matches. Scalars compare exactly.

Failures report bounded, redacted response metadata rather than raw response
bodies or header values.

## Safety defaults

- Ten-second connection and request timeout.
- Redirects are not followed.
- Request and response bodies are limited to 2 MiB.
- Interrupted requests preserve the thread interrupt flag.
