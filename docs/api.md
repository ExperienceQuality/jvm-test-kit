# JVM Test Kit REST API

`com.xq:jvm-test-kit` is a small Java 21 library for HTTP-based service E2E
tests with JUnit Jupiter. Version 3 loads REST configuration from consumer test resources.

## Configure service endpoint

Consumer test resources must contain one classpath-root `xq.properties`:

```properties
xq.rest.base-uri=http://localhost:8080/
```

```java
@XqTest
class RoutineE2ETest {
    // Xq.rest() reads xq.rest.base-uri from xq.properties.
}
```

`@XqTest` creates and closes an isolated context for every invocation.
`Xq.rest()` is valid only inside the active test thread.

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
        .hasStatus(201)
        .containsJson("{\"name\":\"Strength A\"}");
```

`RestApi` supports GET, POST, and PUT. Paths are service-relative and cannot
escape the configured base path. GET rejects a body. JSON bodies are encoded as
UTF-8 and receive `Content-Type: application/json` unless one was supplied.

`RestResponse` exposes `statusCode`, immutable `headers`, defensive-copy `body`,
`bodyUtf8`, `bodyAs(Charset)`, and `should`.

## JSON matching

`hasJsonBody` performs exact JsonUnit comparison. `containsJson` ignores extra
object fields and array items, and array order. `hasJsonPathValue` checks a
single JsonUnit path.

Failures report bounded, redacted response metadata rather than raw response
bodies or header values.

## Safety defaults

## Database clients

Named JDBC clients use `xq.database.<name>.url`, `.username`, and `.password`
properties. Values may be direct values or explicit `-env VARIABLE` references.
`Xq.db().get("name")` is valid only during an active `@XqTest` invocation.
`withConnection` scopes and closes a connection; `transaction` disables
autocommit, commits successful callbacks, and rolls back failures. Credentials
are never included in diagnostics. Consumers provide the PostgreSQL JDBC
driver and own SQL, migrations, and cleanup.

- Ten-second connection and request timeout.
- Redirects are not followed.
- Request and response bodies are limited to 2 MiB.
- Interrupted requests preserve the thread interrupt flag.
