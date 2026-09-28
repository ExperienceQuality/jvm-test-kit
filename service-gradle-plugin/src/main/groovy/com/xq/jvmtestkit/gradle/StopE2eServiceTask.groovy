package com.xq.jvmtestkit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

@DisableCachingByDefault(because = 'Stops an external service process')
abstract class StopE2eServiceTask extends DefaultTask {
    @Internal
    abstract RegularFileProperty getServiceJar()

    @Internal
    abstract RegularFileProperty getPidFile()

    @TaskAction
    void stopService() {
        Path pid = pidFile.get().asFile.toPath()
        Optional<Long> recordedPid = ServiceProcessSupport.readPid(pid)
        if (recordedPid.empty) {
            logger.lifecycle('E2E service is already stopped')
            return
        }

        long value = recordedPid.get()
        Optional<ProcessHandle> process = ProcessHandle.of(value)
        if (process.empty || !process.get().alive) {
            deletePid(pid)
            logger.lifecycle('Removed stale E2E service PID {}', value)
            return
        }

        Path expectedJar = serviceJar.get().asFile.toPath().toAbsolutePath().normalize()
        if (!ServiceProcessSupport.ownsJar(process.get(), expectedJar)) {
            throw new GradleException("Refusing to stop PID ${value} because it is not the service launched from ${expectedJar}")
        }

        ServiceProcessSupport.terminate(process.get(), Duration.ofSeconds(10))
        deletePid(pid)
        logger.lifecycle('Stopped E2E service PID {}', value)
    }

    private static void deletePid(Path pid) {
        try {
            Files.deleteIfExists(pid)
        } catch (IOException exception) {
            throw new GradleException("Could not remove service PID file ${pid}", exception)
        }
    }
}
