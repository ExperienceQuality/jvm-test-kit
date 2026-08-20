package com.xq.jvmtestkit.postgres;

import java.util.concurrent.TimeUnit;

final class DockerConditions {
    private DockerConditions() {
    }

    static boolean available() {
        try {
            Process process = new ProcessBuilder("docker", "info")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception exception) {
            return false;
        }
    }
}
