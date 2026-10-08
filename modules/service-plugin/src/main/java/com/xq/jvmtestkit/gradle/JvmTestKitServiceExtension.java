package com.xq.jvmtestkit.gradle;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import java.time.Duration;

public abstract class JvmTestKitServiceExtension {
    /** Library version resolved for the generated E2E source set. */
    public abstract Property<String> getJvmTestKitVersion();

    public abstract Property<String> getArtifactName();

    public abstract Property<String> getHealthUrl();

    public abstract Property<Duration> getStartupTimeout();

    public abstract ListProperty<String> getJvmArgs();

    public abstract MapProperty<String, String> getEnvironment();

    /** Package used for the generated Spring Cucumber bootstrap class and default glue. */
    public abstract Property<String> getCucumberPackage();

    /** Name used for the generated Spring Cucumber bootstrap class. */
    public abstract Property<String> getCucumberSpringConfigurationClass();

    /** Additional glue packages merged into generated junit-platform.properties. */
    public abstract ListProperty<String> getCucumberGlue();

}
