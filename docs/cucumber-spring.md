# Optional Spring Cucumber adapter

The default integration uses PicoContainer. Spring support is an optional,
separate `com.xq:jvm-test-kit-spring:<releaseVersion>` artifact. A test runtime
must use exactly one Cucumber object factory: do not combine
`cucumber-picocontainer` and `cucumber-spring`.

For the service Gradle plugin, select Spring instead of the default Pico mode:

```groovy
jvmTestKitService {
    cucumberDependencyInjection = 'spring'
}
```

The Spring adapter provides the company Cucumber context as a scenario-scoped
bean. Each Spring consumer defines exactly one `@CucumberContextConfiguration`
with a dedicated `@ContextConfiguration`, importing the adapter's
`XqCucumberSpringTestConfiguration` plus any company test utility
`@TestConfiguration` classes. This is deliberately separate from the
application-under-test context. The JUnit Platform Cucumber engine remains the
execution entry point. See the [service plugin guide](../modules/service-plugin/README.md).

The artifact aligns `cucumber-spring` with the same Cucumber BOM as the core
kit. Cucumber's [state and DI guide](https://cucumber.io/docs/cucumber/state/)
recommends one DI module and scenario-scoped state; the adapter follows that
model without changing the default Pico integration.

`fixtures/spring-consumer` demonstrates the standard JUnit Platform + Cucumber
engine path, an explicit utility-only `@ContextConfiguration`, a Spring-managed
scenario-scoped company API utility injected into steps, and an in-process HTTP
API. It runs independently from `fixtures/clean-consumer`, which remains the
Pico-backed example:

```bash
./gradlew -p fixtures/spring-consumer clean check \
  -PkitVersion=3.0.0-test \
  -PtestRepository=/path/to/staged-maven-repository
```
