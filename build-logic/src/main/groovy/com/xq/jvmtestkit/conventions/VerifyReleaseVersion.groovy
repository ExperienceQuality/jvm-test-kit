package com.xq.jvmtestkit.conventions

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/** Rejects non-SemVer and SNAPSHOT versions before a product is published. */
abstract class VerifyReleaseVersion extends DefaultTask {
    private static final def SEMVER = ~/\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?/

    /** Gradle property that supplies the version, named in failure messages. */
    @Input
    abstract Property<String> getVersionProperty()

    @Input
    abstract Property<String> getExampleVersion()

    @Input
    abstract Property<String> getCandidate()

    @TaskAction
    void verify() {
        String candidate = this.candidate.get()
        String property = versionProperty.get()
        if (!SEMVER.matcher(candidate).matches()) {
            throw new GradleException("${property} must be SemVer (for example ${exampleVersion.get()}): ${candidate}")
        }
        if (candidate.endsWith('-SNAPSHOT')) {
            throw new GradleException("${property} must not be a SNAPSHOT: ${candidate}")
        }
    }
}
