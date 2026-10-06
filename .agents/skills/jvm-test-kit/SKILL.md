---
name: jvm-test-kit
description: Integrate and use com.xq:jvm-test-kit in JVM service E2E tests.
---

# JVM test-kit integration

Use this skill when adding or changing service E2E tests that consume
`com.xq:jvm-test-kit`.

## Dependency

- Pin one immutable released version.
- Resolve GitHub Packages with `GITHUB_ACTOR` and `GITHUB_TOKEN`.
- Give CI `packages: read`; never print package credentials.
- Never commit a `mavenLocal` fallback or copied kit source.

## Configuration and JUnit lifecycle

- Annotate the E2E class with `@XqTest`.
- Add exactly one classpath-root `xq.properties` to consumer test resources.
- Set required `xq.rest.base-uri` property. Use service-relative paths in requests.
- `@XqTest` loads configuration and creates one REST helper per test invocation.
- Use `Xq.rest()` only inside the active test invocation. Do not construct `RestApiConfig`.
- Keep service-specific payloads and scenarios in the consumer repository.

## Greenfield Cucumber setup

- For consumers using `com.xq.jvm-test-kit.service-plugin`, configure the
  `jvmTestKitService` extension before generating files:

  ```groovy
  jvmTestKitService {
      cucumberPackage = 'com.example.acceptance'
      cucumberGlue = ['com.example.steps']
  }
  ```

- Run `./gradlew initJvmTestKitSpringCucumber` to create the Spring bootstrap
  under `src/e2e/java` and patch
  `src/e2e/resources/junit-platform.properties`.
- The generator always retains `com.xq.jvmtestkit.cucumber` and
  `com.xq.jvmtestkit.cucumber.XqCucumberPlugin`, merges configured glue, and
  preserves unrelated properties.
- The generated class extends
  `com.xq.jvmtestkit.cucumber.spring.XqCucumberSpringConfiguration` and must
  retain a direct `@CucumberContextConfiguration` annotation; Cucumber does
  not inherit that marker from an abstract superclass.
- Add consumer-owned Spring utility beans with `@ContextConfiguration` on the
  generated class. Do not import the application-under-test context unless the
  consumer explicitly needs that behavior.
- The task never overwrites an existing Java configuration class. Review the
  preserved file and update it manually when an existing project already has a
  Spring Cucumber bootstrap.

For direct consumers that do not apply the service plugin, create the same
properties and subclass manually, following `docs/cucumber-spring.md`.

## HTTP usage

- Use `RestRequest.empty()` when no headers or body are needed.
- Use `RestRequest.builder()` for headers and JSON bodies.
- Use service-relative paths only.
- Assert with `response.should().hasStatus(...).containsJson(...)` or
  `RestResponseAssert.assertThat(response)`.
- Use `hasJsonBody(...)` for exact JsonUnit comparison and `containsJson(...)` for
  extra-field/extra-array-item tolerant matching. Use `hasJsonPathValue(...)` for paths.
- Use `bodyUtf8()` and the consumer's JSON mapper to extract generated values.
- Do not add RestAssured wrappers, custom lifecycle extensions, or copied kit classes.

## Verification

- Compile the consumer E2E source set.
- For a generated greenfield setup, run `./gradlew initJvmTestKitSpringCucumber`
  and inspect the generated files before compiling.
- Run `./gradlew cucumberE2eTest` or the consumer's equivalent Cucumber task.
- Run the consumer's ordinary verification and real E2E suite.
- Prove the pinned package resolves remotely in CI.
