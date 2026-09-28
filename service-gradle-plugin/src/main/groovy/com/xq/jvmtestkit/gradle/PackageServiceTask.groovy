package com.xq.jvmtestkit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import java.nio.file.Files
import java.nio.file.StandardCopyOption

@DisableCachingByDefault(because = 'The task only creates the conventional CI artifact copy')
abstract class PackageServiceTask extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getBootJar()

    @OutputFile
    abstract RegularFileProperty getOutputJar()

    @TaskAction
    void packageService() {
        def source = bootJar.get().asFile.toPath()
        def destination = outputJar.get().asFile.toPath()
        Files.createDirectories(destination.parent)
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
    }
}
