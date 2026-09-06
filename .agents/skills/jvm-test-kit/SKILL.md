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

## JUnit lifecycle

- Annotate the E2E class with `@XqTest`.
- Configure one `RestApiConfig` in `@BeforeEach` with `Xq.rest(config)`.
- Use `Xq.rest()` only inside the active test invocation.
- Keep service-specific payloads and scenarios in the consumer repository.

## HTTP usage

- Use `RestRequest.empty()` when no headers or body are needed.
- Use `RestRequest.builder()` for headers and JSON bodies.
- Use service-relative paths only.
- Assert with `response.should().status(...).matchJson(...)`.
- Use `bodyUtf8()` and the consumer's JSON mapper to extract generated values.
- Do not add RestAssured wrappers, custom lifecycle extensions, or copied kit classes.

## Verification

- Compile the consumer E2E source set.
- Run the consumer's ordinary verification and real E2E suite.
- Prove the pinned package resolves remotely in CI.
