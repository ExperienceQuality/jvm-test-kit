# JVM Test Kit REST API

`com.xq:jvm-test-kit` is a small Java 21 library for HTTP-based service E2E
tests with JUnit Jupiter. Version 3 loads REST configuration from consumer test resources.

## Configure service endpoint

Consumer test resources must contain exactly one classpath-root `xq.yaml`.
Use flat dotted keys in this YAML mapping:

```yaml
xq.rest.base-uri: http://localhost:8080/
```

```java
@XqTest
class RoutineE2ETest {
    // Xq.rest() reads xq.rest.base-uri from xq.yaml.
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

## Downstream stub API

The optional stub server is configured with a nested `stub` block in `xq.yaml`:

```yaml
stub:
  enabled: true
  host: 127.0.0.1
  port: 18089
  reset-before-scenario: true
  isolate-scenarios: true
```

Environment variables with the corresponding `XQ_STUB_*` names override these
values. The server binds a real TCP listener, uses an ephemeral port when the
configured port is `0`, and exposes `StubApi.baseUri()` after `start()`.
`Xq.stub()` starts it lazily unless `enabled` is true. A fixed port is required
when a separately launched service reads its dependency URL before startup.

`StubApi` accepts WireMock `MappingBuilder` and `RequestPatternBuilder` values,
including native method, URL, query, header, body, JSON, response, delay,
fault, verification, and count-matching builders. `failConnection` simulates a
connection reset; a true connection-refused case requires an unused or stopped
endpoint.

Each JUnit invocation and Cucumber scenario receives a unique test ID. In
isolated mode mappings and verification patterns are automatically constrained
by `X-Xq-Test-Id`, and resets remove only that scenario's mappings and journal
entries. `Xq.rest()` adds the header automatically. Services that cannot
propagate it must disable isolation and run scenarios serially; overlapping
scenarios fail fast.

## Public API reference

`RestApi` exposes `get(path)`, `get(path, request)`, `post(path, request)`, and
`put(path, request)`. `RestRequest.empty()` creates an empty immutable request;
`RestRequest.builder()` supports `header`, `headers`, `jsonBody`, and `build`.
Headers reject blank names and line breaks. `body()` returns a defensive copy.

`RestResponse` exposes `statusCode()`, immutable `headers()`, defensive-copy
`body()`, `bodyUtf8()`, `bodyAs(Charset)`, and `should()`. Assertions support
`hasStatus`, `hasHeader(name)`, `hasHeader(name, value)`, `hasEmptyBody`,
`hasBody`, `hasJsonBody`, `containsJson`, `hasJsonPathValue`, and
`containsJsonAtPath`. Exact JSON uses JsonUnit comparison; containment allows
extra object fields and array items while ignoring array order.

Database consumers configure named JDBC clients in the same `xq.yaml` with
`xq.database.<name>.url`, `.username`, and `.password`. `Xq.db().get(name)` returns a scoped
`DatabaseClient`; `withConnection` closes its connection and `transaction`
commits success or rolls back failure. `DatabaseRegistry.names()` lists names,
and `close()` closes all clients. Consumer supplies the JDBC driver.

The test-kit module also exports Cucumber integration: `XqCucumberContext`,
`XqCucumberHooks`, `XqCucumberPlugin`, `XqJsonTable`, and JSON-table helpers.
Spring consumers use the Spring integration included in the `jvm-test-kit` artifact.

## Database clients

Named JDBC clients use `xq.database.<name>.url`, `.username`, and `.password`
YAML keys. Values may be direct values or explicit `-env VARIABLE` references.
`Xq.db().get("name")` is valid only during an active `@XqTest` invocation.
`withConnection` scopes and closes a connection; `transaction` disables
autocommit, commits successful callbacks, and rolls back failures. Credentials
are never included in diagnostics. Consumers provide the PostgreSQL JDBC
driver and own SQL, migrations, and cleanup.

- Ten-second connection and request timeout.
- Redirects are not followed.
- Request and response bodies are limited to 2 MiB.
- Interrupted requests preserve the thread interrupt flag.
