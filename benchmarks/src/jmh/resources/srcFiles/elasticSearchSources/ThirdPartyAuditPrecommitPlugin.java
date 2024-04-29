/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle.internal.precommit;

import org.elasticsearch.gradle.dependencies.CompileOnlyResolvePlugin;
import org.elasticsearch.gradle.internal.ExportElasticsearchBuildResourcesTask;
import org.elasticsearch.gradle.internal.conventions.precommit.PrecommitPlugin;
import org.elasticsearch.gradle.internal.info.BuildParams;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;
import java.nio.file.Path;

public class ThirdPartyAuditPrecommitPlugin extends PrecommitPlugin {

    public static final String JDK_JAR_HELL_CONFIG_NAME = "jdkJarHell";
    public static final String LIBS_ELASTICSEARCH_CORE_PROJECT_PATH = ":libs:elasticsearch-core";

    @Override
    public TaskProvider<? extends Task> createTask(Project project) {
        project.getPlugins().apply(CompileOnlyResolvePlugin.class);
        project.getConfigurations().create("forbiddenApisCliJar");
        project.getDependencies().add("forbiddenApisCliJar", "de.thetaphi:forbiddenapis:3.6");
        Configuration jdkJarHellConfig = project.getConfigurations().create(JDK_JAR_HELL_CONFIG_NAME);
        if (project.getPath().equals(LIBS_ELASTICSEARCH_CORE_PROJECT_PATH) == false) {
            var elasticsearchCoreProject = project.findProject(LIBS_ELASTICSEARCH_CORE_PROJECT_PATH);
            if (elasticsearchCoreProject != null) {
                project.getDependencies().add(JDK_JAR_HELL_CONFIG_NAME, elasticsearchCoreProject);
            }
        }

        TaskProvider<ExportElasticsearchBuildResourcesTask> resourcesTask = project.getTasks()
            .register("thirdPartyAuditResources", ExportElasticsearchBuildResourcesTask.class);
        Path resourcesDir = project.getBuildDir().toPath().resolve("third-party-audit-config");
        resourcesTask.configure(t -> {
            t.setOutputDir(resourcesDir.toFile());
            t.copy("forbidden/third-party-audit.txt");
        });
        TaskProvider<ThirdPartyAuditTask> audit = project.getTasks().register("thirdPartyAudit", ThirdPartyAuditTask.class);
        project.getTasks().withType(ThirdPartyAuditTask.class).configureEach(t -> {
            Configuration runtimeConfiguration = project.getConfigurations().getByName("runtimeClasspath");
            Configuration compileOnly = project.getConfigurations()
                .getByName(CompileOnlyResolvePlugin.RESOLVEABLE_COMPILE_ONLY_CONFIGURATION_NAME);
            t.setClasspath(runtimeConfiguration.plus(compileOnly));
            t.getJarsToScan().from(runtimeConfiguration.fileCollection(dep -> {
                return dep.getGroup() != null && dep.getGroup().startsWith("org.elasticsearch") == false;
            }));
            t.dependsOn(resourcesTask);
            if (BuildParams.getIsRuntimeJavaHomeSet()) {
                t.getJavaHome().set(project.provider(BuildParams::getRuntimeJavaHome).map(File::getPath));
            }
            t.getTargetCompatibility().set(project.provider(BuildParams::getRuntimeJavaVersion));
            t.setSignatureFile(resourcesDir.resolve("forbidden/third-party-audit.txt").toFile());
            t.getJdkJarHellClasspath().from(jdkJarHellConfig);
            t.getForbiddenAPIsClasspath().from(project.getConfigurations().getByName("forbiddenApisCliJar").plus(compileOnly));
        });
        return audit;
    }

}
