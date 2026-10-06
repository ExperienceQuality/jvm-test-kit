# JVM consumer guide

This guide is for an application repository that consumes the internal XQ JVM
test platform. The application owns `.feature` files and business step
definitions. Cucumber and the JUnit Platform remain the execution interface;
the kit supplies scenario context, HTTP utilities, JSON composition, hooks,
and redacted observability.

## Add the released kit

The artifacts are internal GitHub Packages Maven artifacts. Give the consumer
repository read access and provide credentials through CI environment
variables:

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
    testImplementation 'com.xq:jvm-test-kit:3.0.0'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test {
    useJUnitPlatform()
}
```

Pin exact versions. Do not use dynamic selectors such as `3.+`.

## Spring Cucumber integration

The integration uses Cucumber's JUnit Platform engine and Spring. If the
consumer applies `com.xq.jvm-test-kit.service-plugin`, the recommended
greenfield setup is:

```groovy
jvmTestKitService {
    cucumberPackage = 'com.example.acceptance'
    cucumberGlue = ['com.example.steps']
}
```

Then generate the bootstrap files:

```bash
./gradlew initJvmTestKitSpringCucumber
```

The task creates the Spring Cucumber configuration under `src/e2e/java` and
merges the required entries into
`src/e2e/resources/junit-platform.properties`. It is safe to rerun: unrelated
properties are preserved and an existing Java configuration class is never
overwritten. The generated class extends
`XqCucumberSpringConfiguration`; add consumer-owned Spring test utilities with
`@ContextConfiguration` when needed.

For an existing or direct Gradle consumer, configure the application glue and
company plugin manually in `src/test/resources/junit-platform.properties`
or the equivalent `src/e2e/resources` location:

```properties
cucumber.glue=com.xq.jvmtestkit.cucumber,example.steps
cucumber.plugin=com.xq.jvmtestkit.cucumber.XqCucumberPlugin
```

Set the service base URL in the consumer's classpath-root `xq.yaml`:

```yaml
testBaseUrl: http://127.0.0.1:8080/
```

For `@XqTest`, use the same file with the flat dotted REST key:

```yaml
xq.rest.base-uri: http://127.0.0.1:8080/
```

The kit supports `xq.yaml` only; do not add `xq.properties`.

The IDE, Maven, Gradle, or JUnit Console Launcher still discovers and runs
Cucumber scenarios. The company kit is not a replacement runner.

## Business-language steps and request tables

Inject the scenario-scoped `XqCucumberContext` into application steps and use
field paths as table headers:

```gherkin
When I submit an order:
  | customer.id | customer.profile.active | items[0].sku | items[0].quantity |
  | cust-123    | true                    | SKU-1        | 2                 |
```

```java
public final class OrderSteps {
    private final XqCucumberContext test;

    public OrderSteps(XqCucumberContext test) {
        this.test = test;
    }

    @When("I submit an order:")
    public void submitOrder(DataTable table) {
        JsonNode body = XqJsonTable.compose(table);
        test.rest().post("/orders", RestRequest.builder().jsonBody(body).build());
    }
}
```

Numbers, booleans, and `null` remain JSON values. Duplicate/conflicting
headers, blank cells, malformed paths, and sparse arrays fail with table
coordinates. Use a doc string for arbitrary JSON documents.

## Response assertions

Use the same composer for response tables. Exact assertions compare the full
document or selected path, including array order and length. Contains
assertions permit extra object fields and unordered extra array items:

```java
@Then("the order response includes:")
void orderResponse(DataTable table) {
    response.should()
        .hasJsonPathValue("$.status", "accepted")
        .containsJson(XqJsonTable.compose(table));
}

@Then("the customer contains:")
void customer(DataTable table) {
    response.should().containsJsonAtPath(
        "$.customer", XqJsonTable.compose(table));
}

@Then("the returned items include:")
void items(DataTable table) {
    response.should().containsJsonAtPath(
        "$.items", XqJsonTable.compose(table));
}
```

Keep the `RestResponse` returned by the When step and apply multiple Then
assertions to it. The kit does not replay the request. Assertion failures
redact response bodies while retaining the path, status, and byte count needed
for diagnosis.

## Optional Spring integration

The service plugin supplies the Spring Cucumber dependencies automatically.
Direct consumers add the Spring dependencies and use exactly one Cucumber
object factory:

```groovy
testImplementation 'com.xq:jvm-test-kit:3.0.0'
testImplementation 'io.cucumber:cucumber-spring'
testImplementation platform('org.springframework.boot:spring-boot-dependencies:4.1.1')
testImplementation 'org.springframework.boot:spring-boot-test'
testImplementation 'org.springframework:spring-test'
```

Define one consumer class with `@CucumberContextConfiguration` that extends
`XqCucumberSpringConfiguration`. The base class supplies the kit's
`XqCucumberSpringTestConfiguration`; add a consumer `@ContextConfiguration`
only when registering company test utility beans. Application steps can then
constructor-inject those utility beans. `@CucumberContextConfiguration` must
remain directly on the consumer class because Cucumber does not inherit that
marker from an abstract superclass.

The Spring integration manages test utilities and scenario scope; it does not boot
or replace the application context unless the consumer explicitly chooses to
include application configuration.

## Ownership and safety

Consumers own service startup, migrations, application data, feature files,
and business steps. The kit owns scenario context, HTTP clients, fixtures,
hooks, lifecycle events, and bounded diagnostics. Keep GitHub credentials and
secrets referenced by `xq.yaml` in CI or local secret management, never in
committed feature files.
