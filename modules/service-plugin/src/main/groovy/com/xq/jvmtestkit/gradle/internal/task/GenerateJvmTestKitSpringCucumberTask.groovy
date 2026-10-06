package com.xq.jvmtestkit.gradle.internal.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import java.nio.file.Files
import java.nio.file.Path

/**
 * Creates the small amount of consumer-owned source/configuration required by
 * the Spring Cucumber convention without taking ownership of consumer files.
 */
@DisableCachingByDefault(because = 'The task patches existing consumer-owned files and must inspect their current contents.')
abstract class GenerateJvmTestKitSpringCucumberTask extends DefaultTask {
    @Input
    abstract Property<String> getCucumberPackage()

    @Input
    abstract Property<String> getConfigurationClassName()

    @Input
    abstract ListProperty<String> getCucumberGlue()

    @OutputFile
    abstract RegularFileProperty getConfigurationFile()

    @OutputFile
    abstract RegularFileProperty getPropertiesFile()

    @TaskAction
    void generate() {
        validatePackage(cucumberPackage.get())
        validateClassName(configurationClassName.get())

        Path configurationPath = configurationFile.get().asFile.toPath()
        String configurationSource = configurationSource()
        if (Files.notExists(configurationPath)) {
            Files.createDirectories(configurationPath.parent)
            Files.writeString(configurationPath, configurationSource)
            logger.lifecycle("Generated Spring Cucumber configuration: ${configurationPath}")
        } else if (Files.readString(configurationPath) == configurationSource) {
            logger.lifecycle("Spring Cucumber configuration already initialized: ${configurationPath}")
        } else {
            logger.lifecycle("Preserved existing Spring Cucumber configuration: ${configurationPath}")
        }

        Path propertiesPath = propertiesFile.get().asFile.toPath()
        if (Files.notExists(propertiesPath)) {
            Files.createDirectories(propertiesPath.parent)
            Files.writeString(propertiesPath, generatedProperties())
            logger.lifecycle("Generated Cucumber properties: ${propertiesPath}")
        } else {
            String original = Files.readString(propertiesPath)
            String patched = patchProperties(original)
            if (patched != original) {
                Files.writeString(propertiesPath, patched)
                logger.lifecycle("Patched Cucumber properties: ${propertiesPath}")
            } else {
                logger.lifecycle("Cucumber properties already initialized: ${propertiesPath}")
            }
        }
    }

    private String configurationSource() {
        String packageName = cucumberPackage.get()
        """package ${packageName};

import com.xq.jvmtestkit.cucumber.spring.XqCucumberSpringConfiguration;
import io.cucumber.spring.CucumberContextConfiguration;

/**
 * Generated bootstrap for Cucumber's Spring object factory.
 * Add consumer-owned @TestConfiguration classes with @ContextConfiguration
 * when the consumer has Spring-managed test utilities.
 */
@CucumberContextConfiguration
public class ${configurationClassName.get()} extends XqCucumberSpringConfiguration {
}
"""
    }

    private String generatedProperties() {
        "cucumber.glue=${gluePackages()}\n" +
                "cucumber.plugin=com.xq.jvmtestkit.cucumber.XqCucumberPlugin\n"
    }

    private String patchProperties(String source) {
        String requiredGlue = gluePackages()
        String requiredPlugin = 'com.xq.jvmtestkit.cucumber.XqCucumberPlugin'
        List<String> lines = source.split('\\R', -1) as List
        boolean glueFound = false
        boolean pluginFound = false
        List<String> patched = lines.collect { String line ->
            if (line.startsWith('cucumber.glue=')) {
                glueFound = true
                return "cucumber.glue=${GenerateJvmTestKitSpringCucumberTask.mergeCsv(line.substring('cucumber.glue='.length()), requiredGlue)}"
            }
            if (line.startsWith('cucumber.plugin=')) {
                pluginFound = true
                return "cucumber.plugin=${GenerateJvmTestKitSpringCucumberTask.mergeCsv(line.substring('cucumber.plugin='.length()), requiredPlugin)}"
            }
            line
        }
        if (!glueFound) {
            patched.add("cucumber.glue=${requiredGlue}")
        }
        if (!pluginFound) {
            patched.add("cucumber.plugin=${requiredPlugin}")
        }
        String result = patched.join('\n')
        if (!source.endsWith('\n')) {
            result
        } else if (!result.endsWith('\n')) {
            result + '\n'
        } else {
            result
        }
    }

    private String gluePackages() {
        mergeCsv('com.xq.jvmtestkit.cucumber,' + cucumberGlue.get().join(','), cucumberPackage.get())
    }

    private static String mergeCsv(String existing, String additions) {
        List<String> values = []
        (existing + ',' + additions).split(',').each { String value ->
            String trimmed = value.trim()
            if (!trimmed.isEmpty() && !values.contains(trimmed)) {
                values.add(trimmed)
            }
        }
        values.join(',')
    }

    private static void validatePackage(String packageName) {
        if (!(packageName ==~ /[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)*/)) {
            throw new GradleException("jvmTestKitService.cucumberPackage must be a valid Java package")
        }
    }

    private static void validateClassName(String className) {
        if (!(className ==~ /[A-Z][a-zA-Z0-9_]*/)) {
            throw new GradleException("jvmTestKitService.cucumberSpringConfigurationClass must be a Java class name")
        }
    }
}
