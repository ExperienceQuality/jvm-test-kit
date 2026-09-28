# XQ Service Gradle Plugin

`com.xq.jvm-test-kit.service-plugin` is a versioned convention plugin for Java 21 and
Spring Boot services. Its published implementation coordinates are
`com.xq.jvm-test-kit:service-plugin:<pluginVersion>`; Gradle also publishes
the standard plugin marker. Convention wiring lives in the precompiled Groovy
script `src/main/groovy/com.xq.jvm-test-kit.service-plugin.gradle`, including
its typed extension, task types, validation, and process-safety helpers.

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
