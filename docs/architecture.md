# Repository architecture

## Product ownership

The repository is one Gradle multi-project build with two products. Each
module owns its source, tests, version input, publication, and consumer
contract.

| Boundary | Owns | Does not own |
| --- | --- | --- |
| `modules/test-kit` | `com.xq:jvm-test-kit`, Jupiter and Cucumber scenario lifecycle, REST client values and assertions, compatibility report, SBOM | Service process startup, service builds, infrastructure |
| `modules/spring-adapter` | Optional `com.xq:jvm-test-kit-spring`; scenario-scoped company context and Spring Cucumber object factory integration | Default DI selection, AUT Spring context, Pico integration |
| `modules/service-plugin` | Plugin ID and marker, `jvmTestKitService` DSL, Java/Spring conventions, Cucumber/Jupiter E2E tasks, packaging and owned-process tasks | Test-kit public Java API, databases, containers, deployment |
| Root build | Module inclusion, aggregate `clean`/`assemble`/`check`, `verifyAll`, legacy library task facade | Product source or independent version selection |
| `fixtures/clean-consumer` | External resolution proof for the staged library | Published production code |
| `ci` and `.github/workflows` | Immutable staging, evidence, promotion, release routing | Product behavior |

New code belongs in the module that owns its public contract. The root facade
exists for compatibility with earlier library automation; new scripts should
prefer explicit `:test-kit:` or `:service-plugin:` task paths.

## Public boundaries

The library public surface is under `com.xq.jvmtestkit.junit` and
`com.xq.jvmtestkit.rest`; its detailed contract is in [api.md](api.md).

The plugin public surface is intentionally narrow:

- plugin ID `com.xq.jvm-test-kit.service-plugin`;
- the `jvmTestKitService` extension properties;
- documented task names and output paths;
- the generated plugin marker and implementation coordinates.

`ServiceConventionsPlugin` is a thin Gradle entry point.
`JvmTestKitServiceExtension` defines the DSL. Configuration, validation, task
implementations, and process metadata under `com.xq.jvmtestkit.gradle.internal`
are implementation details, not consumer APIs.

## Plugin lifecycle flow

`packageService` validates configuration, runs `bootJar`, then writes the
runnable JAR under `build/service`. `startE2eService` launches that exact JAR,
writes `build/application.pid`, `build/application.pid.owner`, and
`build/application.log`, then waits for a 2xx health response.

The ownership record binds the PID to launch identity and JAR path.
`stopE2eService` verifies that identity before termination. It never kills a
foreign live PID. Failure paths perform bounded cleanup and preserve the
thread's interrupt status.

The `e2eTest` and `cucumberE2eTest` tasks are separate from process management:
callers decide when to start and stop the service and any external
infrastructure. Cucumber runs through its JUnit Platform engine and the
standard Console Launcher. Consumer `junit-platform.properties` owns glue and
plugin configuration so IDEs and Gradle use the same Cucumber engine setup.

## Compatibility

- Both products require Java 21.
- Plugin consumer behavior is exercised with Gradle 8.14.5.
- Library binary compatibility is checked against its configured released
  baseline.
- Library and plugin versions are intentionally independent.
- Moving implementation classes or changing root aggregation must not change
  published coordinates, plugin ID, DSL properties, documented tasks, or
  output paths.
