package com.xq.jvmtestkit.conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

/**
 * Shared publication contract: sources JAR, POM licence and SCM metadata, the
 * isolated Test repository, GitHub Packages, and version verification before
 * any repository publication.
 */
final class PublishingConventionsPlugin implements Plugin<Project> {
    static final String REPOSITORY_URL = 'https://github.com/ExperienceQuality/jvm-test-kit'
    static final String PACKAGES_URL = 'https://maven.pkg.github.com/ExperienceQuality/jvm-test-kit'

    @Override
    void apply(Project project) {
        project.pluginManager.apply(JavaConventionsPlugin)
        project.pluginManager.apply(MavenPublishPlugin)
        project.extensions.getByType(JavaPluginExtension).withSourcesJar()

        def extension = project.extensions.create('xqPublishing', PublishingConventionsExtension)
        extension.testRepository.convention(
                project.layout.dir(project.providers.gradleProperty('testRepository').map { project.file(it) })
                        .orElse(project.layout.buildDirectory.dir('test-repository'))
        )

        def publishing = project.extensions.getByType(PublishingExtension)
        publishing.repositories.maven {
            name = 'Test'
            url = { -> extension.testRepository.get().asFile }
        }
        publishing.repositories.maven {
            name = 'GitHubPackages'
            url = project.uri(PACKAGES_URL)
            credentials {
                username = project.providers.environmentVariable('GITHUB_ACTOR').orNull
                password = project.providers.environmentVariable('GITHUB_TOKEN').orNull
            }
        }

        publishing.publications.withType(MavenPublication).configureEach {
            pom {
                url = REPOSITORY_URL
                licenses {
                    license {
                        name = 'The Apache License, Version 2.0'
                        url = 'https://www.apache.org/licenses/LICENSE-2.0.txt'
                    }
                }
                scm {
                    connection = 'scm:git:https://github.com/ExperienceQuality/jvm-test-kit.git'
                    developerConnection = 'scm:git:ssh://github.com/ExperienceQuality/jvm-test-kit.git'
                    url = REPOSITORY_URL
                }
            }
        }

        project.tasks.withType(PublishToMavenRepository).configureEach {
            dependsOn(project.tasks.withType(VerifyReleaseVersion))
        }
    }
}
