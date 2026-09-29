package com.xq.jvmtestkit.gradle.internal.task;

import com.xq.jvmtestkit.gradle.internal.ServiceConfiguration;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.time.Duration;

@DisableCachingByDefault(because = "Validation has no output")
public abstract class ValidateJvmTestKitServiceConfigurationTask extends DefaultTask {
    @Input
    public abstract Property<String> getArtifactName();

    @Input
    public abstract Property<String> getHealthUrl();

    @Input
    public abstract Property<Duration> getStartupTimeout();

    @TaskAction
    public void validate() {
        ServiceConfiguration.validateArtifactName(getArtifactName().get());
        ServiceConfiguration.validateHealthUrl(getHealthUrl().get());
        ServiceConfiguration.validateStartupTimeout(getStartupTimeout().get());
    }
}
