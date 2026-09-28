package com.xq.jvmtestkit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import java.time.Duration

@DisableCachingByDefault(because = 'Validation has no output')
abstract class ValidateJvmTestKitServiceConfigurationTask extends DefaultTask {
    @Input
    abstract Property<String> getArtifactName()

    @Input
    abstract Property<String> getHealthUrl()

    @Input
    abstract Property<Duration> getStartupTimeout()

    @TaskAction
    void validate() {
        ServiceConfiguration.validateArtifactName(artifactName.get())
        ServiceConfiguration.validateHealthUrl(healthUrl.get())
        ServiceConfiguration.validateStartupTimeout(startupTimeout.get())
    }
}
