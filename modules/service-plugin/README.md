# Service Gradle Plugin

The `service-plugin` module owns the convention plugin
`com.xq.jvm-test-kit.service-plugin`. Its implementation is published as
`com.xq.jvm-test-kit:service-plugin:<pluginVersion>` together with Gradle's
generated plugin marker. `pluginVersion` is independent from the JVM Test Kit
library's `releaseVersion`.

The plugin supplies Java 21, Spring Boot, dependency management, JUnit, Cucumber,
the `e2e` source set, runnable-JAR packaging, and process lifecycle tasks. Database,
container, and deployment infrastructure remain consumer or CI
responsibilities.

## Apply the plugin

Configure authenticated plugin resolution in `settings.gradle`:

```groovy
pluginManagement {
    repositories {
        maven {
            url = uri('https://maven.pkg.github.com/ExperienceQuality/jvm-test-kit')
            credentials {
                username = providers.environmentVariable('GITHUB_ACTOR').orNull
                password = providers.environmentVariable('GITHUB_TOKEN').orNull
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}
```

Pin an immutable version in `build.gradle`:

```groovy
plugins {
    id 'com.xq.jvm-test-kit.service-plugin' version '0.1.0'
}

jvmTestKitService {
    artifactName = 'example-service.jar'
    healthUrl = 'http://127.0.0.1:8080/actuator/health'
    startupTimeout = java.time.Duration.ofSeconds(120)
    jvmArgs = ['-XX:MaxRAMPercentage=75']
    environment = [SPRING_PROFILES_ACTIVE: 'e2e']
}
```

The consumer compatibility suite runs with Gradle 8.14.5. The plugin selects a
Java 21 toolchain. Cucumber features and step definitions belong under
`src/e2e/resources/features` and `src/e2e/java` respectively. Configure company
and application glue in the consumer's `src/e2e/resources/junit-platform.properties`:

```properties
cucumber.glue=com.xq.jvmtestkit.cucumber,example.steps
cucumber.plugin=com.xq.jvmtestkit.cucumber.XqCucumberPlugin
```

Set `XQ_TEST_BASE_URL` in the test process environment. Constructor-inject
`XqCucumberContext` into step classes to access `rest()`. The company context is
field paths as table column headers. Each data row becomes one nested JSON
object, and multiple rows become an array. JSON literals are parsed and
unquoted bare text becomes a string.

PicoContainer is the default DI backend. Spring consumers can opt into the
separate `com.xq:jvm-test-kit-spring` artifact by setting
`cucumberDependencyInjection = 'spring'`; the plugin then supplies Spring
Cucumber integration instead of PicoContainer. Never add both Cucumber object
factory modules to one test runtime. Spring consumers provide one dedicated
`@CucumberContextConfiguration` importing the adapter's company utility test
configuration; it does not load the application-under-test context. See
[optional Spring integration](../../docs/cucumber-spring.md).

## DSL contract

| Property | Default | Purpose |
| --- | --- | --- |
| `artifactName` | `<project-name>.jar` | Safe JAR basename copied to `build/service`. |
| `healthUrl` | `http://127.0.0.1:8080/actuator/health` | HTTP(S) endpoint polled until it returns 2xx. |
| `startupTimeout` | 120 seconds | Bounded wait for startup. |
| `jvmArgs` | empty | Arguments passed before `-jar`. |
| `environment` | empty | Non-secret overrides added to the inherited process environment. |
| `cucumberDependencyInjection` | `pico` | Cucumber DI backend: `pico` or optional `spring`. Exactly one object factory is selected. |

Keep credentials in the inherited environment. Do not place secrets in the
extension, JVM arguments, source, or Gradle properties.

## Task lifecycle

| Task | Contract |
| --- | --- |
| `validateJvmTestKitServiceConfiguration` | Rejects unsafe artifact names, non-HTTP(S) health URLs, and invalid timeouts. |
| `packageService` | Runs `bootJar` and copies the result to `build/service/<artifactName>`. |
| `ci` | Runs `check` and `packageService`. |
| `e2eTest` | Runs Jupiter tests in `src/e2e` against a service managed by the caller. |
| `cucumberE2eTest` | Runs Cucumber features with Gradle's `Test` task and the Cucumber JUnit Platform engine. Forwards Gradle process `cucumber.*` system properties. |
| `e2e` | Runs both `e2eTest` and `cucumberE2eTest`. |
| `startE2eService` | Packages and starts the JAR, records PID plus launch identity, writes `build/application.log`, and waits for health. |
| `stopE2eService` | Stops only a live process whose PID and recorded launch identity match the packaged JAR. |

Startup failure, timeout, or interruption triggers bounded cleanup and removes
state only after the owned process stops. Missing and dead PIDs are handled
idempotently. Malformed state or a foreign live PID fails closed and is
preserved for investigation.

## Verify and stage

```bash
./gradlew --no-daemon :service-plugin:test :service-plugin:validatePlugins \
  -PpluginVersion=0.1.0-test
./gradlew --no-daemon :service-plugin:publishAllPublicationsToTestRepository \
  -PpluginVersion=0.1.0-test -PtestRepository=/tmp/jvm-test-kit-repository
```

Remote publication is performed only by the repository release pipeline. See
the [architecture](../../docs/architecture.md) and
[release contract](../../docs/ci-cd.md) for ownership and promotion details.
