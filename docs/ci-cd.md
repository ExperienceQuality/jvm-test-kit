# CI and release contract

The repository-owned workflows in `.github/workflows` are immutable inputs to
the v2 rebuild. The Gradle project supplies every task and artifact path they
invoke.

## Verification

Pull requests and pushes to `main` run `clean check`, an isolated Maven-local
publication, the clean-consumer fixture, CycloneDX SBOM generation, and JUnit
report sanitization. The clean consumer compiles and executes only the published
public API.

## Release

Stable releases use immutable `vMAJOR.MINOR.PATCH` tags on protected `main`.
The tag selects `releaseVersion`; the workflow rejects an already published
Maven version and publishes `com.xq:jvm-test-kit` to GitHub Packages.

Consumers need an immutable version, the GitHub Packages repository, and
credentials supplied through `GITHUB_ACTOR` and `GITHUB_TOKEN`. GitHub Actions
requires `packages: read`, and the package must grant the consumer repository
access. Never log credentials or commit a `mavenLocal` fallback.

Version 2 is an intentional compatibility reset. Older polling, PostgreSQL,
OpenAPI, and low-level HTTP APIs are not part of the v2 contract.
