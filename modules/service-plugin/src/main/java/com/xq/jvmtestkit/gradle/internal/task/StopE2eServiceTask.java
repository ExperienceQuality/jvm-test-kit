package com.xq.jvmtestkit.gradle.internal.task;

import com.xq.jvmtestkit.gradle.internal.lifecycle.ServiceProcessSupport;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

@DisableCachingByDefault(because = "Stops an external service process")
public abstract class StopE2eServiceTask extends DefaultTask {
    @Internal
    public abstract RegularFileProperty getServiceJar();

    @Internal
    public abstract RegularFileProperty getPidFile();

    @TaskAction
    public void stopService() {
        Path pidFile = getPidFile().get().getAsFile().toPath();
        Path ownershipFile = ServiceProcessSupport.ownershipFile(pidFile);
        Optional<Long> recordedPid = ServiceProcessSupport.readPid(pidFile);
        if (recordedPid.isEmpty()) {
            getLogger().lifecycle("E2E service is already stopped");
            return;
        }

        long pid = recordedPid.get();
        Optional<ProcessHandle> process = ProcessHandle.of(pid);
        if (process.isEmpty() || !process.get().isAlive()) {
            ServiceProcessSupport.deleteState(pidFile);
            getLogger().lifecycle("Removed stale E2E service PID {}", pid);
            return;
        }

        Path expectedJar = getServiceJar().get().getAsFile().toPath().toAbsolutePath().normalize();
        if (!ServiceProcessSupport.ownsLaunch(process.get(), expectedJar, ownershipFile)) {
            throw new GradleException("Refusing to stop PID " + pid + " because it is not the service launched from " + expectedJar);
        }

        ServiceProcessSupport.terminate(process.get(), Duration.ofSeconds(10));
        ServiceProcessSupport.deleteState(pidFile);
        getLogger().lifecycle("Stopped E2E service PID {}", pid);
    }
}
