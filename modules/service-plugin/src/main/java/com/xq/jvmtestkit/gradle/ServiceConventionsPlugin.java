package com.xq.jvmtestkit.gradle;

import com.xq.jvmtestkit.gradle.internal.ServicePluginConfigurer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public final class ServiceConventionsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        ServicePluginConfigurer.configure(project);
    }
}
