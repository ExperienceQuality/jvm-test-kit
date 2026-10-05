package com.xq.jvmtestkit.gradle.internal;

import com.xq.jvmtestkit.gradle.JvmTestKitServiceExtension;
import com.xq.jvmtestkit.gradle.internal.task.PackageServiceTask;
import com.xq.jvmtestkit.gradle.internal.task.StartE2eServiceTask;
import com.xq.jvmtestkit.gradle.internal.task.StopE2eServiceTask;
import com.xq.jvmtestkit.gradle.internal.task.ValidateJvmTestKitServiceConfigurationTask;
import io.spring.gradle.dependencymanagement.DependencyManagementPlugin;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.springframework.boot.gradle.plugin.SpringBootPlugin;
import org.springframework.boot.gradle.tasks.bundling.BootJar;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class ServicePluginConfigurer {
    private static final String JVM_TEST_KIT_VERSION = "3.0.0";

    private ServicePluginConfigurer() {
    }

    public static void configure(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply(SpringBootPlugin.class);
        project.getPluginManager().apply(DependencyManagementPlugin.class);

        JvmTestKitServiceExtension extension = project.getExtensions().create(
                "jvmTestKitService",
                JvmTestKitServiceExtension.class
        );
        extension.getArtifactName().convention(project.getName() + ".jar");
        extension.getHealthUrl().convention("http://127.0.0.1:8080/actuator/health");
        extension.getStartupTimeout().convention(Duration.ofSeconds(120));
        extension.getJvmArgs().convention(List.of());
        extension.getEnvironment().convention(Map.of());
        extension.getCucumberDependencyInjection().convention("pico");

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(21));

        configureRepositories(project.getRepositories(), project);
        configureDependenciesAndSourceSets(project, extension);
        configureTasks(project, extension);
    }

    private static void configureRepositories(RepositoryHandler repositories, Project project) {
        repositories.mavenCentral();
        repositories.maven(repository -> {
            repository.setName("XqGitHubPackages");
            repository.setUrl("https://maven.pkg.github.com/ExperienceQuality/jvm-test-kit");
            repository.content(content -> content.includeGroup("com.xq"));
            repository.credentials(PasswordCredentials.class, credentials -> {
                credentials.setUsername(project.getProviders().environmentVariable("GITHUB_ACTOR").getOrNull());
                credentials.setPassword(project.getProviders().environmentVariable("GITHUB_TOKEN").getOrNull());
            });
        });
    }

    private static void configureDependenciesAndSourceSets(Project project, JvmTestKitServiceExtension extension) {
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        SourceSet e2e = sourceSets.create("e2e");

        project.getDependencies().add("testImplementation", "org.junit.jupiter:junit-jupiter:6.0.0");
        project.getDependencies().add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:6.0.0");
        project.getDependencies().add(e2e.getImplementationConfigurationName(), "org.junit.jupiter:junit-jupiter-api:6.0.0");
        project.getDependencies().add(e2e.getImplementationConfigurationName(), "com.xq:jvm-test-kit:" + JVM_TEST_KIT_VERSION);
        project.getDependencies().add(e2e.getRuntimeOnlyConfigurationName(), "org.junit.jupiter:junit-jupiter-engine:6.0.0");
        project.getDependencies().add(e2e.getRuntimeOnlyConfigurationName(), "org.junit.platform:junit-platform-launcher:6.0.0");
        project.getDependencies().add(e2e.getImplementationConfigurationName(),
                project.getDependencies().platform("io.cucumber:cucumber-bom:8.0.2"));
        project.getDependencies().add(e2e.getImplementationConfigurationName(), "io.cucumber:cucumber-java");
        project.getDependencies().add(e2e.getImplementationConfigurationName(),
                extension.getCucumberDependencyInjection().map(value -> switch (value.toLowerCase(java.util.Locale.ROOT)) {
                    case "pico" -> "io.cucumber:cucumber-picocontainer";
                    case "spring" -> "com.xq:jvm-test-kit-spring:" + JVM_TEST_KIT_VERSION;
                    default -> throw new org.gradle.api.GradleException(
                            "jvmTestKitService.cucumberDependencyInjection must be 'pico' or 'spring'");
                }));
        project.getDependencies().add(e2e.getRuntimeOnlyConfigurationName(), "io.cucumber:cucumber-junit-platform-engine");
    }

    private static void configureTasks(Project project, JvmTestKitServiceExtension extension) {
        project.getTasks().named("test", Test.class).configure(Test::useJUnitPlatform);

        SourceSet e2e = project.getExtensions().getByType(SourceSetContainer.class).getByName("e2e");
        TaskProvider<Test> e2eTest = project.getTasks().register("e2eTest", Test.class, task -> {
            task.setDescription("Runs the JVM Test Kit end-to-end suite against a running service.");
            task.setGroup("verification");
            task.dependsOn(project.getTasks().named(e2e.getClassesTaskName()));
            task.setTestClassesDirs(e2e.getOutput().getClassesDirs());
            task.setClasspath(e2e.getRuntimeClasspath());
            task.useJUnitPlatform();
            task.getOutputs().upToDateWhen(ignored -> false);
        });

        TaskProvider<Test> cucumberE2eTest = project.getTasks().register(
                "cucumberE2eTest", Test.class, task -> {
                    task.setDescription("Runs Cucumber features through the standard JUnit Platform engine.");
                    task.setGroup("verification");
                    task.dependsOn(project.getTasks().named(e2e.getClassesTaskName()));
                    task.setTestClassesDirs(e2e.getOutput().getClassesDirs());
                    task.setClasspath(e2e.getRuntimeClasspath());
                    task.useJUnitPlatform(options -> options.includeEngines("cucumber"));
                    task.getSystemProperties().putAll(project.getProviders().systemPropertiesPrefixedBy("cucumber.").get());
                    task.getOutputs().upToDateWhen(ignored -> false);
                }
        );

        project.getTasks().register("e2e", DefaultTask.class, task -> {
            task.setDescription("Runs end-to-end tests against the configured service URI.");
            task.setGroup("verification");
            task.dependsOn(e2eTest, cucumberE2eTest);
        });

        TaskProvider<ValidateJvmTestKitServiceConfigurationTask> validate = project.getTasks().register(
                "validateJvmTestKitServiceConfiguration",
                ValidateJvmTestKitServiceConfigurationTask.class,
                task -> {
                    task.setDescription("Validates XQ service convention configuration.");
                    task.setGroup("verification");
                    task.getArtifactName().set(extension.getArtifactName());
                    task.getHealthUrl().set(extension.getHealthUrl());
                    task.getStartupTimeout().set(extension.getStartupTimeout());
                }
        );

        TaskProvider<BootJar> bootJar = project.getTasks().named("bootJar", BootJar.class);
        TaskProvider<PackageServiceTask> packageService = project.getTasks().register(
                "packageService",
                PackageServiceTask.class,
                task -> {
                    task.setDescription("Packages the runnable service JAR in the CI artifact layout.");
                    task.setGroup("build");
                    task.dependsOn(bootJar, validate);
                    task.getBootJar().set(bootJar.flatMap(BootJar::getArchiveFile));
                    task.getOutputJar().set(project.getLayout().getBuildDirectory().file(
                            extension.getArtifactName().map(name -> "service/" + name)
                    ));
                }
        );

        project.getTasks().register("ci", DefaultTask.class, task -> {
            task.setDescription("Runs checks and produces the packaged service artifact.");
            task.setGroup("verification");
            task.dependsOn(project.getTasks().named("check"), packageService);
        });

        JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        Provider<JavaLauncher> launcher = toolchains.launcherFor(
                spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(21))
        );

        TaskProvider<StartE2eServiceTask> start = project.getTasks().register(
                "startE2eService",
                StartE2eServiceTask.class,
                task -> configureStartTask(project, extension, validate, packageService, launcher, task)
        );

        project.getTasks().register("stopE2eService", StopE2eServiceTask.class, task -> {
            task.setDescription("Stops the E2E service process started by startE2eService.");
            task.setGroup("verification");
            task.mustRunAfter(start);
            task.getServiceJar().set(packageService.flatMap(PackageServiceTask::getOutputJar));
            task.getPidFile().set(project.getLayout().getBuildDirectory().file("application.pid"));
        });
    }

    private static void configureStartTask(
            Project project,
            JvmTestKitServiceExtension extension,
            TaskProvider<ValidateJvmTestKitServiceConfigurationTask> validate,
            TaskProvider<PackageServiceTask> packageService,
            Provider<JavaLauncher> launcher,
            StartE2eServiceTask task
    ) {
        task.setDescription("Starts the packaged service and waits for its health endpoint.");
        task.setGroup("verification");
        task.dependsOn(packageService, validate);
        task.getServiceJar().set(packageService.flatMap(PackageServiceTask::getOutputJar));
        task.getJavaExecutable().set(launcher.map(JavaLauncher::getExecutablePath));
        task.getHealthUrl().set(extension.getHealthUrl());
        task.getStartupTimeout().set(extension.getStartupTimeout());
        task.getJvmArgs().set(extension.getJvmArgs());
        task.getEnvironment().set(extension.getEnvironment());
        task.getPidFile().set(project.getLayout().getBuildDirectory().file("application.pid"));
        task.getLogFile().set(project.getLayout().getBuildDirectory().file("application.log"));
    }
}
