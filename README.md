# XQ JVM Test Kit

[![Delivery](https://github.com/ExperienceQuality/jvm-test-kit/actions/workflows/deploy.yml/badge.svg)](https://github.com/ExperienceQuality/jvm-test-kit/actions/workflows/deploy.yml)

This repository publishes the JVM Test Kit library and a service Gradle plugin
for JVM services. The library includes its Cucumber, REST, and optional Spring
test integrations. The root Gradle build aggregates the products and preserves
the legacy JVM Test Kit task names; product code and publication ownership live
in the module directories.

| Product | Source | Public identity | Version input | Release tag |
| --- | --- | --- | --- | --- |
| JVM Test Kit | [`modules/test-kit`](modules/test-kit/README.md) | `com.xq:jvm-test-kit:<version>` | `releaseVersion` | `vMAJOR.MINOR.PATCH` |
| Service Gradle Plugin | [`modules/service-plugin`](modules/service-plugin/README.md) | plugin `com.xq.jvm-test-kit.service-plugin`; implementation `com.xq.jvm-test-kit:service-plugin:<version>` | `pluginVersion` | `plugin-vMAJOR.MINOR.PATCH` |

A library release never implies a plugin release, and a plugin release never
changes the library version. Consumers must pin the exact version of each
product they use.

## Repository guide

- [JVM Test Kit module](modules/test-kit/README.md): install, public API, and
  module verification.
- [Service Gradle Plugin module](modules/service-plugin/README.md): consumer
  configuration, DSL, tasks, and lifecycle safety.
- [Architecture](docs/architecture.md): module ownership, public boundaries,
  root compatibility facade, and runtime flow.
- [Library API](docs/api.md): detailed JVM Test Kit behavior.
- [Consumer guide](docs/consumer-guide.md): dependencies, Cucumber setup,
  response tables, and optional Spring integration.
- [CI and release contract](docs/ci-cd.md): immutable staging, tag routing,
  permissions, verification, and rollback.

## Database clients

Configure named JDBC clients in the consumer’s single classpath-root `xq.yaml`:

```yaml
xq.database.main.url: jdbc:postgresql://127.0.0.1:5432/app
xq.database.main.username: -env XQ_DB_USER
xq.database.main.password: -env XQ_DB_PASSWORD
```

Inside `@XqTest`, use `Xq.db().get("main")` for scoped connections or explicit
transactions. The kit does not bundle a JDBC driver; consumers own SQL,
migrations, schemas, and cleanup.

## Local verification

Run the complete repository gate:

```bash
./gradlew --no-daemon check :service-plugin:validatePlugins
```

Run one product independently:

```bash
./gradlew --no-daemon :test-kit:check -PreleaseVersion=2.0.0-test
./gradlew --no-daemon :service-plugin:test :service-plugin:validatePlugins \
  -PpluginVersion=0.1.0-test
```

Development publications must use an isolated repository, never a committed
`mavenLocal()` fallback:

```bash
./gradlew --no-daemon :test-kit:publishMavenJavaPublicationToTestRepository \
  -PreleaseVersion=2.0.0-test -PtestRepository=/tmp/jvm-test-kit-repository
./gradlew --no-daemon :service-plugin:publishAllPublicationsToTestRepository \
  -PpluginVersion=0.1.0-test -PtestRepository=/tmp/jvm-test-kit-repository
```

GitHub Packages consumers need `packages: read` and credentials supplied at
runtime through `GITHUB_ACTOR` and `GITHUB_TOKEN`. Do not commit credentials,
dynamic versions, or local-repository fallbacks.
