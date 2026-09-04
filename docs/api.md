# JVM Test Kit API

`com.xq:jvm-test-kit` provides JUnit lifecycle helpers, bounded HTTP calls,
REST assertions, polling, PostgreSQL fixtures, and OpenAPI response checks.

## JUnit integration

Annotate a test class with `@XqTest`. The extension creates one context per
test invocation and closes it after teardown.

```java
@XqTest
class HealthTest {
    @BeforeEach
    void configure() {
        Xq.rest(RestApiConfig.at(URI.create("http://localhost:8080/")));
    }

    @Test
    void health() {
        Xq.rest().get("/health").should().status(200);
    }
}
```

`Xq.rest(config)` configures the REST helper. Reusing it with an unequal
configuration in one invocation fails. `Xq.rest()` retrieves the configured
helper. Calling either method outside an active `@XqTest` context fails.

## REST API

`RestApi` exposes service-relative `get`, `post`, `put`, and `delete` calls.
Each returns the same fluent helper; call `should()` after a request.

```java
Xq.rest().get("/users").should()
        .status(200)
        .equalToJson("{\"id\":1,\"name\":\"Ada\"}");
```

Implemented assertions:

- `status(int)` checks exact status code.
- `equalToJson(String)` compares parsed JSON trees, ignoring object key order
  while preserving array order. Failures include a friendly unified diff.
- `equalToJson(Object)` serializes the expected value as JSON, then applies the
  same strict comparison.
- `match(String)` and `match(Object)` perform lenient structural matching:
  expected object fields and array elements must exist, object key order and
  array order are ignored, and extra actual fields/elements are allowed.

`equalToJsonSchema(String)` remains reserved for future implementation.

## Bounded HTTP client

`ServiceHttpClient` executes a `ServiceHttpRequest` against one absolute HTTP(S)
base URI. Requests cannot escape that base path. Responses are immutable.

```java
HttpPolicy policy = HttpPolicy.builder()
        .requestTimeout(Duration.ofSeconds(5))
        .maxResponseBodyBytes(2 * 1024 * 1024)
        .build();

ServiceHttpClient client = new ServiceHttpClient(
        URI.create("https://service.example/api/"), policy);

ServiceHttpResponse response = client.execute(
        ServiceHttpRequest.post("/users")
                .header("Content-Type", "application/json")
                .bodyUtf8("{\"name\":\"Ada\"}")
                .build());
```

`ServiceHttpRequest` supports `builder(method, pathAndQuery)`, plus `get`,
`post`, `put`, and `delete` factories. Builder methods: `header`, `body`,
`bodyUtf8`, and `build`.

`ServiceHttpResponse` exposes `statusCode`, `headers`, `body`, `bodyUtf8`,
`bodyAs(Charset)`, and `sanitizedDiagnostics`.

`HttpPolicy` supports `defaults()` and a builder with `requestTimeout`,
`maxRedirects`, `maxRequestBodyBytes`, and `maxResponseBodyBytes`. Redirects
stay inside the configured service base and never follow automatically.

## Polling

```java
PollingPolicy policy = PollingPolicy.of(Duration.ofSeconds(10), Duration.ofMillis(200));
Poller.await(() -> repository.isReady(), policy);
```

`Poller` also supports construction with a policy and instance `await`. A
`CheckedCondition` may throw checked exceptions. Timeout and interruption are
reported explicitly; interruption status is preserved.

## PostgreSQL fixture

```java
try (PostgresFixture postgres = new PostgresFixture(PostgresFixtureConfig.defaults())) {
    postgres.start();
    postgres.executeSql("create table users (id int)");
    try (Connection connection = postgres.openConnection()) {
        // use connection
    }
}
```

`PostgresFixtureConfig` provides `defaults()` and a builder for digest-pinned
`imageName`, `databaseName`, `username`, and `password`. Fixture methods:
`start`, `jdbcUrl`, `openConnection`, `executeSql`, `config`,
`sanitizedDiagnostics`, and `close`.

## OpenAPI response contracts

```java
OpenApiResponseContract contract = OpenApiResponseContract.fromSpec(
        spec, new Operation("/pets", "GET", "listPets"),
        200, "application/json");

ContractCheck result = contract.check(response);
if (!result.passed()) {
    result.violations().forEach(v -> log(v.sanitizedDiagnostics()));
}
```

`Operation` identifies path, HTTP method, and optional operation ID.
`OpenApiResponseContract` supports OpenAPI 3.0 response schemas with internal
component references. `ContractCheck` exposes `success`, `failure`, `passed`,
and `violations`. Each `ContractViolation` exposes `location`, `message`, and
`sanitizedDiagnostics`.

External references, OpenAPI 3.1, composition keywords, discriminators, and
unsupported schema types are rejected.

## Safety and lifecycle

- Keep request and response limits explicit for untrusted services.
- Use `sanitizedDiagnostics`; do not log raw credentials or bodies.
- Close `PostgresFixture` and allow the JUnit extension to close REST helpers.
- Configure one REST base URI per test invocation.
