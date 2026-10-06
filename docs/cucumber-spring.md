# Optional Spring Cucumber integration

The integration uses Cucumber Spring. Spring support is included in the
`com.xq:jvm-test-kit:<releaseVersion>` artifact.

The service Gradle plugin configures the Spring Cucumber runtime automatically:

```groovy
jvmTestKitService { }
```

For a new service, the plugin can create the bootstrap files:

```bash
./gradlew initJvmTestKitSpringCucumber
```

Configure the package and consumer glue before running the task when the
defaults are not appropriate:

```groovy
jvmTestKitService {
    cucumberPackage = 'com.example.acceptance'
    cucumberGlue = ['com.example.steps']
}
```

The task creates a `CucumberSpringConfiguration` extending
`XqCucumberSpringConfiguration` and merges the required
`cucumber.glue`/`cucumber.plugin` entries into
`src/e2e/resources/junit-platform.properties`. It does not overwrite an
existing Java configuration class or unrelated properties.

For a direct fixture dependency, add the library and Spring integration
dependencies explicitly:

```groovy
testImplementation 'com.xq:jvm-test-kit:3.0.0'
testImplementation 'io.cucumber:cucumber-spring'
testImplementation platform('org.springframework.boot:spring-boot-dependencies:4.1.1')
testImplementation 'org.springframework.boot:spring-boot-test'
testImplementation 'org.springframework:spring-test'
```

The Spring integration provides the company Cucumber context as a scenario-scoped
bean. Each Spring consumer defines exactly one `@CucumberContextConfiguration`
with a dedicated `@ContextConfiguration`, importing the library's
`XqCucumberSpringTestConfiguration` plus any company test utility
`@TestConfiguration` classes. This is deliberately separate from the
application-under-test context. The JUnit Platform Cucumber engine remains the
execution entry point. See the [service plugin guide](../modules/service-plugin/README.md).

The library aligns `cucumber-spring` with the same Cucumber BOM as the core kit.
Cucumber's [state and DI guide](https://cucumber.io/docs/cucumber/state/)
recommends one DI module and scenario-scoped state; the adapter follows that
model with one consistent object factory.

The `:spring-consumer` module demonstrates the standard JUnit Platform +
Cucumber engine path, an explicit utility-only `@ContextConfiguration`, a
Spring-managed scenario-scoped company API utility injected into steps, and an
in-process HTTP API:

```bash
./gradlew :spring-consumer:check
```
