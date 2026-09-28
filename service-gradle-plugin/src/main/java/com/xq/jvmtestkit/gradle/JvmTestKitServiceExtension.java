package com.xq.jvmtestkit.gradle;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import java.time.Duration;

public abstract class JvmTestKitServiceExtension {
    public abstract Property<String> getArtifactName();

    public abstract Property<String> getHealthUrl();

    public abstract Property<Duration> getStartupTimeout();

    public abstract ListProperty<String> getJvmArgs();

    public abstract MapProperty<String, String> getEnvironment();
}
