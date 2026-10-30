package com.xq.jvmtestkit.conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

/** Java toolchain, Maven Central, and JUnit Platform defaults shared by every project. */
final class JavaConventionsPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.apply(JavaPlugin)

        def libs = project.extensions.getByType(VersionCatalogsExtension).named('libs')
        def javaVersion = libs.findVersion('java').get().requiredVersion
        project.extensions.getByType(JavaPluginExtension).toolchain.languageVersion
                .set(JavaLanguageVersion.of(javaVersion))

        project.repositories.mavenCentral()
        project.tasks.withType(Test).configureEach { useJUnitPlatform() }
    }
}
