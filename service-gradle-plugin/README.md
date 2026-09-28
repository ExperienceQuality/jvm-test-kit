# XQ Service Gradle Plugin

`com.xq.jvm-test-kit.service-plugin` is a versioned convention plugin for Java 21 and
Spring Boot services. Its published implementation coordinates are
`com.xq.jvm-test-kit:service-plugin:<pluginVersion>`; Gradle also publishes
the standard plugin marker.

Run producer verification from the repository root:

```shell
./gradlew --no-daemon -p service-gradle-plugin clean test validatePlugins \
  -PpluginVersion=0.1.0-test
```

Publish only to the isolated test repository during development:

```shell
./gradlew --no-daemon -p service-gradle-plugin \
  publishAllPublicationsToTestRepository -PpluginVersion=0.1.0-test
```

Remote publication uses `GITHUB_ACTOR` and `GITHUB_TOKEN`. It requires explicit
release approval. `pluginVersion` is independent from the JVM Test Kit
library's `releaseVersion`.

## How the Java classes connect to a consumer `build.gradle`

The consumer does not import these classes directly. Gradle resolves the plugin
ID in the `plugins` block, loads the generated marker, then invokes
`ServiceConventionsPlugin.apply(Project)`. That method applies Java, Spring
Boot, and dependency management, creates the `jvmTestKitService` extension, and
registers the convention tasks.

| Java class | Gradle build role |
| --- | --- |
| `ServiceConventionsPlugin` | Entry point called by the plugin ID; wires plugins, repositories, dependencies, extension, and tasks. |
| `JvmTestKitServiceExtension` | Backs the `jvmTestKitService { ... }` block in the consumer build. |
| `ValidateJvmTestKitServiceConfigurationTask` | Validates artifact name, health URL, and startup timeout before packaging or startup. |
| `PackageServiceTask` | Implements `packageService`; copies `bootJar` to `build/service/<artifactName>`. |
| `StartE2eServiceTask` | Implements `startE2eService`; launches the packaged JAR, writes PID/log files, and waits for health. |
| `StopE2eServiceTask` | Implements `stopE2eService`; verifies process ownership before termination. |
| `ServiceProcessSupport` | Shared PID parsing, process ownership, graceful termination, and bounded force-stop logic. |
| `ServiceConfiguration` | Shared validation for safe artifact names, HTTP(S) health URLs, and bounded timeouts. |

For this consumer build:

```groovy
plugins {
    id 'com.xq.jvm-test-kit.service-plugin' version '0.1.0'
}

jvmTestKitService {
    artifactName = 'example-service.jar'
}
```

Gradle creates the project model first, then task dependencies determine
execution order. `packageService` depends on `bootJar`; `startE2eService`
depends on `packageService`; `e2e` depends on `e2eTest`. PostgreSQL and other
external infrastructure remain consumer or CI responsibilities.

The Java classes are kept in a dedicated package so each task has one focused
maintenance surface. The consumer-facing contract stays in the plugin ID,
extension properties, task names, and published marker—not in Java class names.
