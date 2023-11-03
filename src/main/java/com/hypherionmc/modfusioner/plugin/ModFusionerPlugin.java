/*
 * This file is part of ModFusioner, licensed under the GNU Lesser General Public License v2.1.
 *
 * This project is based on, and contains code from https://github.com/PacifistMC/Forgix, licensed under the same license.
 * See their license here: https://github.com/PacifistMC/Forgix/blob/main/LICENSE
 *
 * Copyright HypherionSA and Contributors
 * Forgix Code Copyright by their contributors and Ran-Mewo
 */
package com.hypherionmc.modfusioner.plugin;

import com.hypherionmc.modfusioner.Constants;
import com.hypherionmc.modfusioner.task.JarFuseTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

/**
 * @author HypherionSA
 * Main Gradle Plugin Class
 */
public class ModFusionerPlugin implements Plugin<Project> {

    public static Project rootProject;
    public static Logger logger;
    public static FusionerExtension modFusionerExtension;

    @Override
    public void apply(Project project) {
        // We only want to apply the project to the Root project
        if (project != project.getRootProject())
            return;

        rootProject = project.getRootProject();
        logger = project.getLogger();

        // Register the extension
        modFusionerExtension = rootProject.getExtensions().create(Constants.EXTENSION_NAME, FusionerExtension.class);

        // Register the task
        TaskProvider<JarFuseTask> task = rootProject.getTasks().register(Constants.TASK_NAME, JarFuseTask.class);
        task.configure(fusioner -> {
            fusioner.setGroup(Constants.TASK_GROUP);
            fusioner.setDescription("Merge multiple jars into a single jar, for multi mod loader projects");
        });

        // Check for task dependencies and register them on the main task
        project.subprojects(cc -> cc.afterEvaluate(ccc -> {
            if (modFusionerExtension.getForgeConfiguration() != null
                    && modFusionerExtension.getForgeConfiguration().inputTaskName != null
                    && !modFusionerExtension.getForgeConfiguration().inputTaskName.isEmpty()) {
                if (ccc.getName().equals(modFusionerExtension.getForgeConfiguration().getProjectName()))
                    resolveInputTasks(
                            ccc,
                            modFusionerExtension.getForgeConfiguration().getInputTaskName(),
                            modFusionerExtension.getForgeConfiguration().getProjectName(),
                            task
                    );
            }

            if (modFusionerExtension.getFabricConfiguration() != null
                    && modFusionerExtension.getFabricConfiguration().inputTaskName != null
                    && !modFusionerExtension.getFabricConfiguration().inputTaskName.isEmpty()) {
                if (ccc.getName().equals(modFusionerExtension.getFabricConfiguration().getProjectName()))
                    resolveInputTasks(
                            ccc,
                            modFusionerExtension.getFabricConfiguration().getInputTaskName(),
                            modFusionerExtension.getFabricConfiguration().getProjectName(),
                            task
                    );
            }

            if (modFusionerExtension.getQuiltConfiguration() != null
                    && modFusionerExtension.getQuiltConfiguration().inputTaskName != null
                    && !modFusionerExtension.getQuiltConfiguration().inputTaskName.isEmpty()) {
                if (ccc.getName().equals(modFusionerExtension.getQuiltConfiguration().getProjectName()))
                    resolveInputTasks(
                            ccc,
                            modFusionerExtension.getQuiltConfiguration().getInputTaskName(),
                            modFusionerExtension.getQuiltConfiguration().getProjectName(),
                            task
                    );
            }

            if (modFusionerExtension.getCustomConfigurations() != null && !modFusionerExtension.getCustomConfigurations().isEmpty()) {
                modFusionerExtension.getCustomConfigurations().forEach(c -> {
                    if (ccc.getName().equals(c.getProjectName()) && c.getInputTaskName() != null && !c.getInputTaskName().isEmpty())
                        resolveInputTasks(ccc, c.getInputTaskName(), c.getProjectName(), task);
                });
            }
        }));
    }

    /**
     * Try to locate the correct task to run on the subproject
     * @param project - Sub project being processed
     * @param inTask - The name of the task that will be run
     * @param inProject - The name of the project the task is on
     * @param mainTask - The FuseJars task
     */
    private void resolveInputTasks(Project project, Object inTask, String inProject, TaskProvider<JarFuseTask> mainTask) {
        if (inTask == null)
            return;

        Task task = null;

        if (inProject == null || inProject.isEmpty())
            return;

        if (project == null)
            return;

        if (inTask instanceof String) {
            task = project.getTasks().getByName((String) inTask);
        }

        if (!(task instanceof AbstractArchiveTask))
            return;

        rootProject.task("prepareFuseTask" + project.getName()).dependsOn(":" + project.getName() + ":" + task.getName());
        mainTask.get().dependsOn("prepareFuseTask" + project.getName());
    }
}
