# JVM Test Kit library

The `test-kit` module owns the Java 21 library published as
`com.xq:jvm-test-kit:<releaseVersion>`. It provides a small JUnit Jupiter API
for service-relative HTTP requests, immutable responses, and status/JSON
assertions against a service that is already running.

The module does not own service startup, database or container lifecycle,
polling, retries, or service-specific payload builders. Service build and
local process conventions belong to the sibling
[Service Gradle Plugin](../service-plugin/README.md).

## Install

```groovy
repositories {
    mavenCentral()
    maven {
        url = uri('https://maven.pkg.github.com/ExperienceQuality/jvm-test-kit')
        credentials {
            username = providers.environmentVariable('GITHUB_ACTOR').orNull
            password = providers.environmentVariable('GITHUB_TOKEN').orNull
        }
    }
}

dependencies {
    testImplementation 'com.xq:jvm-test-kit:3.0.0'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test {
    useJUnitPlatform()
}
```

Use an exact released version. CI needs `packages: read`, and the package must
grant the consumer repository access.

## Quick start

Add exactly one classpath-root `xq.properties` to consumer test resources:

```properties
xq.rest.base-uri=http://127.0.0.1:8080/
```

```java
import com.xq.jvmtestkit.junit.Xq;
import com.xq.jvmtestkit.junit.XqTest;
import com.xq.jvmtestkit.rest.RestRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

@XqTest
class RoutineApiE2ETest {
    @Test
    void createsRoutine() {
        Xq.rest().post(
                "/routines",
                RestRequest.builder().jsonBody(Map.of("name", "Strength A")).build()
        ).should().hasStatus(201).containsJson(Map.of("name", "Strength A"));
    }
}
```

See the [API contract](../../docs/api.md) for lifecycle, request, response,
matching, and safety semantics.

## Verify and stage

```bash
./gradlew --no-daemon :test-kit:check -PreleaseVersion=2.0.0-test
./gradlew --no-daemon :test-kit:publishMavenJavaPublicationToTestRepository \
  -PreleaseVersion=2.0.0-test -PtestRepository=/tmp/jvm-test-kit-repository
./gradlew --no-daemon -p fixtures/clean-consumer clean check \
  -PkitVersion=2.0.0-test -PtestRepository=/tmp/jvm-test-kit-repository
```

`check` includes tests and binary compatibility against the configured
released baseline. The root build retains legacy library task aliases, but new
automation should use the explicit `:test-kit:` task path.
