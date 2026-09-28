package com.xq.jvmtestkit.gradle

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

import java.time.Duration

abstract class JvmTestKitServiceExtension {
    abstract Property<String> getArtifactName()

    abstract Property<String> getHealthUrl()

    abstract Property<Duration> getStartupTimeout()

    abstract ListProperty<String> getJvmArgs()

    abstract MapProperty<String, String> getEnvironment()
}
