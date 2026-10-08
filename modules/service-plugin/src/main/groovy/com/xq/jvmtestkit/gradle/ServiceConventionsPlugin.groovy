package com.xq.jvmtestkit.gradle

import com.xq.jvmtestkit.gradle.internal.ServiceConfiguration
import com.xq.jvmtestkit.gradle.internal.task.PackageServiceTask
import com.xq.jvmtestkit.gradle.internal.task.StartE2eServiceTask
import com.xq.jvmtestkit.gradle.internal.task.StopE2eServiceTask
import com.xq.jvmtestkit.gradle.internal.task.ValidateJvmTestKitServiceConfigurationTask
import com.xq.jvmtestkit.gradle.internal.task.GenerateJvmTestKitSpringCucumberTask
import io.spring.gradle.dependencymanagement.DependencyManagementPlugin
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.springframework.boot.gradle.plugin.SpringBootPlugin
import org.springframework.boot.gradle.tasks.bundling.BootJar

import java.time.Duration

/**
 * Gradle-facing wiring for the service conventions plugin.
 *
 * The custom tasks remain Java because they own process lifecycle and cleanup.
 * This class only applies plugins, declares inputs, and connects tasks.
 */
final class ServiceConventionsPlugin implements Plugin<Project> {
    private static final String DEFAULT_JVM_TEST_KIT_VERSION = '3.0.0'

    @Override
    void apply(Project project) {
        project.pluginManager.apply(JavaPlugin)
        project.pluginManager.apply(SpringBootPlugin)
        project.pluginManager.apply(DependencyManagementPlugin)

        def extension = project.extensions.create(
                'jvmTestKitService',
                JvmTestKitServiceExtension
        )
        extension.jvmTestKitVersion.convention(
                project.providers.gradleProperty('jvmTestKitVersion')
                        .orElse(DEFAULT_JVM_TEST_KIT_VERSION)
        )
        extension.artifactName.convention("${project.name}.jar")
        extension.healthUrl.convention('http://127.0.0.1:8080/actuator/health')
        extension.startupTimeout.convention(Duration.ofSeconds(120))
        extension.jvmArgs.convention([])
        extension.environment.convention([:])
        extension.cucumberPackage.convention('example')
        extension.cucumberSpringConfigurationClass.convention('CucumberSpringConfiguration')
        extension.cucumberGlue.convention(['com.xq.jvmtestkit.cucumber'])

        project.extensions.getByType(JavaPluginExtension).toolchain.languageVersion
                .set(JavaLanguageVersion.of(21))

        configureRepositories(project)
        configureDependencies(project, extension)
        configureTasks(project, extension)
    }

    private static void configureRepositories(Project project) {
        project.repositories.mavenCentral()
        project.repositories.maven {
            name = 'XqGitHubPackages'
            url = project.uri('https://maven.pkg.github.com/ExperienceQuality/jvm-test-kit')
            content { includeGroup('com.xq') }
            credentials(PasswordCredentials) {
                username = project.providers.environmentVariable('GITHUB_ACTOR').orNull
                password = project.providers.environmentVariable('GITHUB_TOKEN').orNull
            }
        }
    }

    private static void configureDependencies(Project project, JvmTestKitServiceExtension extension) {
        def sourceSets = project.extensions.getByType(org.gradle.api.tasks.SourceSetContainer)
        SourceSet e2e = sourceSets.create('e2e')
        def implementation = e2e.implementationConfigurationName
        def runtimeOnly = e2e.runtimeOnlyConfigurationName

        project.dependencies.add('testImplementation', 'org.junit.jupiter:junit-jupiter:6.0.0')
        project.dependencies.add('testRuntimeOnly', 'org.junit.platform:junit-platform-launcher:6.0.0')
        project.dependencies.add(implementation, 'org.junit.jupiter:junit-jupiter-api:6.0.0')
        project.dependencies.add(
                implementation,
                extension.jvmTestKitVersion.map { version -> "com.xq:jvm-test-kit:${version}" }
        )
        project.dependencies.add(runtimeOnly, 'org.junit.jupiter:junit-jupiter-engine:6.0.0')
        project.dependencies.add(runtimeOnly, 'org.junit.platform:junit-platform-launcher:6.0.0')
        project.dependencies.add(implementation, project.dependencies.platform('io.cucumber:cucumber-bom:8.0.2'))
        project.dependencies.add(implementation, 'io.cucumber:cucumber-java')
        project.dependencies.add(implementation, 'io.cucumber:cucumber-spring')
        project.dependencies.add(implementation,
                project.dependencies.platform('org.springframework.boot:spring-boot-dependencies:4.1.1'))
        project.dependencies.add(implementation, 'org.springframework.boot:spring-boot-test')
        project.dependencies.add(implementation, 'org.springframework:spring-test')
        project.dependencies.add(runtimeOnly, 'io.cucumber:cucumber-junit-platform-engine')
    }

    private static void configureTasks(Project project, JvmTestKitServiceExtension extension) {
        project.tasks.named('test', Test).configure { useJUnitPlatform() }

        SourceSet e2e = project.extensions.getByType(org.gradle.api.tasks.SourceSetContainer).getByName('e2e')
        def e2eClasses = project.tasks.named(e2e.classesTaskName)

        def e2eTest = project.tasks.register('e2eTest', Test) {
            description = 'Runs the JVM Test Kit end-to-end suite against a running service.'
            group = 'verification'
            dependsOn(e2eClasses)
            testClassesDirs = e2e.output.classesDirs
            classpath = e2e.runtimeClasspath
            useJUnitPlatform()
            outputs.upToDateWhen { false }
        }

        def cucumberE2eTest = project.tasks.register('cucumberE2eTest', Test) {
            description = 'Runs Cucumber features through the standard JUnit Platform engine.'
            group = 'verification'
            dependsOn(e2eClasses)
            testClassesDirs = e2e.output.classesDirs
            classpath = e2e.runtimeClasspath
            useJUnitPlatform { includeEngines('cucumber') }
            systemProperties.putAll(project.providers.systemPropertiesPrefixedBy('cucumber.').get())
            outputs.upToDateWhen { false }
        }

        project.tasks.register('e2e', DefaultTask) {
            description = 'Runs end-to-end tests against the configured service URI.'
            group = 'verification'
            dependsOn(e2eTest, cucumberE2eTest)
        }

        project.tasks.register('initJvmTestKitSpringCucumber', GenerateJvmTestKitSpringCucumberTask) {
            description = 'Generates the Spring Cucumber bootstrap and patches JUnit Platform properties.'
            group = 'build setup'
            cucumberPackage.set(extension.cucumberPackage)
            configurationClassName.set(extension.cucumberSpringConfigurationClass)
            cucumberGlue.set(extension.cucumberGlue)
            propertiesFile.set(project.layout.projectDirectory.file('src/e2e/resources/junit-platform.properties'))
            configurationFile.set(project.layout.projectDirectory.file(
                    extension.cucumberPackage.zip(extension.cucumberSpringConfigurationClass) { packageName, className ->
                        "src/e2e/java/${packageName.replace('.', '/')}/${className}.java"
                    }
            ))
        }

        def validate = project.tasks.register('validateJvmTestKitServiceConfiguration',
                ValidateJvmTestKitServiceConfigurationTask) {
            description = 'Validates XQ service convention configuration.'
            group = 'verification'
            artifactName.set(extension.artifactName)
            healthUrl.set(extension.healthUrl)
            startupTimeout.set(extension.startupTimeout)
        }

        def bootJarTask = project.tasks.named('bootJar', BootJar)
        def packageService = project.tasks.register('packageService', PackageServiceTask) {
            description = 'Packages the runnable service JAR in the CI artifact layout.'
            group = 'build'
            dependsOn(bootJarTask, validate)
            bootJar.set(bootJarTask.flatMap { it.archiveFile })
            outputJar.set(project.layout.buildDirectory.file(
                    extension.artifactName.map { name -> "service/${name}" }
            ))
        }

        project.tasks.register('ci', DefaultTask) {
            description = 'Runs checks and produces the packaged service artifact.'
            group = 'verification'
            dependsOn(project.tasks.named('check'), packageService)
        }

        def launcher = project.extensions.getByType(JavaToolchainService).launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }

        def start = project.tasks.register('startE2eService', StartE2eServiceTask) {
            description = 'Starts the packaged service and waits for its health endpoint.'
            group = 'verification'
            dependsOn(packageService, validate)
            serviceJar.set(packageService.flatMap { it.outputJar })
            javaExecutable.set(launcher.map { it.executablePath })
            healthUrl.set(extension.healthUrl)
            startupTimeout.set(extension.startupTimeout)
            jvmArgs.set(extension.jvmArgs)
            environment.set(extension.environment)
            pidFile.set(project.layout.buildDirectory.file('application.pid'))
            logFile.set(project.layout.buildDirectory.file('application.log'))
        }

        project.tasks.register('stopE2eService', StopE2eServiceTask) {
            description = 'Stops the E2E service process started by startE2eService.'
            group = 'verification'
            mustRunAfter(start)
            serviceJar.set(packageService.flatMap { it.outputJar })
            pidFile.set(project.layout.buildDirectory.file('application.pid'))
        }
    }
}
