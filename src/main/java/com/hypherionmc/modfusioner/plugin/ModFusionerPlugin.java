package com.hypherionmc.modfusioner.plugin;

import com.hypherionmc.modfusioner.Constants;
import com.hypherionmc.modfusioner.task.FuseJarsTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

/**
 * @author HypherionSA
 * Main Gradle Plugin Class
 */
public class ModFusionerPlugin implements Plugin<Project> {

    public static Project rootProject;
    public static Logger logger;
    public static ModFusionerExtension modFusionerExtension;

    @Override
    public void apply(Project project) {
        rootProject = project;
        logger = project.getLogger();

        modFusionerExtension = rootProject.getExtensions().create(Constants.EXTENSION_NAME, ModFusionerExtension.class);

        rootProject.getTasks().register(Constants.TASK_NAME, FuseJarsTask.class).configure(fusioner -> {
            fusioner.setGroup(Constants.TASK_GROUP);
            fusioner.setDescription("Merge multiple jars into a single jar, for multi mod loader projects");
        });
    }
}
