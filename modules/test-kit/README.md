# JVM Test Kit library

The `test-kit` module owns the Java 21 library published as
`com.xq:jvm-test-kit:<releaseVersion>`. It provides a small JUnit Jupiter API
for service-relative HTTP requests, immutable responses, and status/JSON
assertions against a service that is already running.

The module also provides a Cucumber JVM adapter built on Cucumber's JUnit
Platform engine and Spring. Cucumber retains feature discovery,
selection, execution, IDE integration, and exit status. The XQ adapter supplies
scenario-scoped REST context, lifecycle hooks, optional redacted lifecycle
events, and JSON composition from DataTables.

The module does not own service startup, database or container lifecycle,
polling, retries, or service-specific payload builders. Service build and
local process conventions belong to the sibling
[Service Gradle Plugin](../service-plugin/README.md).

## Install

```groovy
repositories {
    mavenCentral()
    maven {
        url = uri('https://maven.pkg.github.com/ExperienceQuality/jvm-test-kit')
        credentials {
            username = providers.environmentVariable('GITHUB_ACTOR').orNull
            password = providers.environmentVariable('GITHUB_TOKEN').orNull
        }
    }
}

dependencies {
    testImplementation 'com.xq:jvm-test-kit:3.0.3'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test {
    useJUnitPlatform()
}
```

Use an exact released version. CI needs `packages: read`, and the package must
grant the consumer repository access.

## Quick start

Add exactly one classpath-root `xq.yaml` to consumer test resources. The kit
uses flat dotted YAML keys; `xq.properties` is not supported:

```yaml
xq.rest.base-uri: http://127.0.0.1:8080/
```

```java
import com.xq.jvmtestkit.junit.Xq;
import com.xq.jvmtestkit.junit.XqTest;
import com.xq.jvmtestkit.rest.RestRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

@XqTest
class RoutineApiE2ETest {
    @Test
    void createsRoutine() {
        Xq.rest().post(
                "/routines",
                RestRequest.builder().jsonBody(Map.of("name", "Strength A")).build()
        ).should().hasStatus(201).containsJson(Map.of("name", "Strength A"));
    }
}
```

See the [API contract](../../docs/api.md) for lifecycle, request, response,
matching, and safety semantics.

## Downstream stubbing

Configure the optional loopback WireMock server in the same `xq.yaml`:

```yaml
stub:
  enabled: true
  host: 127.0.0.1
  port: 18089
  reset-before-scenario: true
  isolate-scenarios: true
```

`XQ_STUB_ENABLED`, `XQ_STUB_HOST`, `XQ_STUB_PORT`,
`XQ_STUB_RESET_BEFORE_SCENARIO`, and `XQ_STUB_ISOLATE_SCENARIOS` override file
values. The server starts eagerly when enabled or lazily on the first
`Xq.stub()` call. Use a fixed port when a separately launched service must read
the dependency URL before it starts; start the test JVM and stub before that
service. Port `0` selects an ephemeral port for in-process orchestration.

The API accepts WireMock's native builders:

```java
StubApi stub = Xq.stub();
stub.stubFor(get(urlPathEqualTo("/api/v1/exercise-logs"))
        .withQueryParam("exerciseName", equalTo("Squat"))
        .willReturn(okJson("[{\"id\":11}]")));
stub.verify(getRequestedFor(urlPathEqualTo("/api/v1/exercise-logs"))
        .withQueryParam("exerciseName", equalTo("Squat")));
```

Scenario-isolated mode scopes mappings and request journals to
`X-Xq-Test-Id`; `Xq.rest()` sends that header automatically. The service must
propagate it to its downstream call for parallel isolation. Set
Set `stub.isolate-scenarios: false` for services that cannot propagate the
header; overlapping scenarios then fail fast.

## Cucumber scenarios

For Cucumber, set `testBaseUrl` in the same classpath-root `xq.yaml`. Configure
company and application glue in consumer `junit-platform.properties`, for example:

```properties
cucumber.glue=com.xq.jvmtestkit.cucumber,example.steps
cucumber.plugin=com.xq.jvmtestkit.cucumber.XqCucumberPlugin
```

When using the service Gradle plugin in a new consumer, configure and generate
the Spring Cucumber bootstrap instead of creating these files by hand:

```groovy
jvmTestKitService {
    cucumberPackage = 'com.example.acceptance'
    cucumberGlue = ['com.example.steps']
}
```

```bash
./gradlew initJvmTestKitSpringCucumber
```

The generator creates the `src/e2e` bootstrap class and patches the matching
`junit-platform.properties` file without overwriting existing Java source.
Direct consumers that do not use the service plugin should follow the [Spring
integration guide](../../docs/cucumber-spring.md).

Constructor-inject `XqCucumberContext` into step classes. It exposes scenario
scoped `rest()`, `runId()`, and `baseUri()` values. Compose structured request
bodies by using field paths as table column headers. Each data row becomes one
JSON object; multiple rows become a JSON array. JSON literals are parsed and
unquoted bare text becomes a string:

```gherkin
When I submit an order:
  | customer.id | customer.profile.active | items[0].sku | items[0].quantity | metadata.source |
  | cust-123    | true                    | SKU-1        | 2                 | acceptance      |
  | cust-456    | false                   | SKU-2        | 1                 | ui              |
```

Duplicate or conflicting path headers, sparse arrays, malformed JSON literals,
and blank cells fail with row and column diagnostics. Object keys containing
`.`, `[` or `]` are not supported by this helper; use a JSON doc string or
Cucumber's native DataTable conversion for those cases.

```java
@When("I submit an order:")
void submitOrders(DataTable table) {
    JsonNode requestBody = XqJsonTable.compose(table);
    test.rest().post("/orders", RestRequest.builder().jsonBody(requestBody).build());
}
```

Reuse the same composer for response assertions. `containsJson` permits extra
object fields and array items and ignores array order; `containsJsonAtPath`
applies those partial-match rules to a JSON path. Use `hasJsonBody` or
`hasJsonPathValue` when the full document or selected subtree must match exactly
(including array order and length):

```java
response.should().containsJson(XqJsonTable.compose(responseTable));
response.should().containsJsonAtPath("$.items", XqJsonTable.compose(itemsTable));
response.should().hasJsonPathValue("$.status", "accepted");
```

All table values retain JSON types: `true`, `null`, and numeric literals are
not strings. A missing path, `null`, a wrong scalar type, and a mismatched
array are distinct failures. Assertion messages redact response bodies.
The `:spring-consumer` module demonstrates Spring-managed company utilities
using business-language steps rather than generic framework REST steps. The
[Spring integration guide](../../docs/cucumber-spring.md) explains the optional
Spring object factory.

## Verify and stage

```bash
./gradlew --no-daemon :test-kit:check -PreleaseVersion=2.0.0-test
./gradlew --no-daemon :test-kit:publishMavenJavaPublicationToTestRepository \
  -PreleaseVersion=2.0.0-test -PtestRepository=/tmp/jvm-test-kit-repository
./gradlew --no-daemon :spring-consumer:check
```

`check` includes tests and binary compatibility against the configured
released baseline. The root build retains legacy library task aliases, but new
automation should use the explicit `:test-kit:` task path.
