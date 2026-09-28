package com.xq.jvmtestkit.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@DisableCachingByDefault(because = "The task only creates the conventional CI artifact copy")
public abstract class PackageServiceTask extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getBootJar();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @TaskAction
    public void packageService() throws IOException {
        var source = getBootJar().get().getAsFile().toPath();
        var destination = getOutputJar().get().getAsFile().toPath();
        Files.createDirectories(destination.getParent());
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }
}
