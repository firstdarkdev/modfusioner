package com.hypherionmc.modfusioner.task;

import com.hypherionmc.modfusioner.Constants;
import com.hypherionmc.modfusioner.actions.JarMergeAction;
import com.hypherionmc.modfusioner.plugin.ModFusionerExtension;
import com.hypherionmc.modfusioner.plugin.ModFusionerPlugin;
import com.hypherionmc.modfusioner.utils.FileChecks;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

import static com.hypherionmc.modfusioner.plugin.ModFusionerPlugin.modFusionerExtension;
import static com.hypherionmc.modfusioner.plugin.ModFusionerPlugin.rootProject;

/**
 * @author HypherionSA
 * The main task of the plugin
 */
public class FuseJarsTask extends DefaultTask {

    @TaskAction
    void meldJars() throws IOException {
        long time = System.currentTimeMillis();

        // Check that all required values are set
        if (modFusionerExtension.getOutJarName() == null || modFusionerExtension.getGroup() == null) {
            ModFusionerPlugin.logger.error("Please configure \"group\" and \"outJarName\" manually!");
            return;
        }

        ModFusionerPlugin.logger.lifecycle("Start Fusing Jars");

        // Get settings from extension
        ModFusionerExtension.ForgeConfiguration forgeConfiguration = modFusionerExtension.getForgeConfiguration();
        ModFusionerExtension.FabricConfiguration fabricConfiguration = modFusionerExtension.getFabricConfiguration();
        ModFusionerExtension.QuiltConfiguration quiltConfiguration = modFusionerExtension.getQuiltConfiguration();

        List<ModFusionerExtension.CustomConfiguration> customConfigurations = modFusionerExtension.getCustomConfigurations();

        // Try to resolve the projects specific in the extension config
        Project forgeProject = null;
        Project fabricProject = null;
        Project quiltProject = null;
        Map<Project, ModFusionerExtension.CustomConfiguration> customProjects = new HashMap<>();
        List<Boolean> validation = new ArrayList<>();

        if (forgeConfiguration != null) {
            try {
                forgeProject = rootProject.getAllprojects().stream().filter(p -> !p.getName().equals(rootProject.getName())).filter(p -> p.getName().equalsIgnoreCase(forgeConfiguration.getProjectName())).findFirst().get();
                validation.add(true);
            } catch (NoSuchElementException ignored) { }
        }

        if (fabricConfiguration != null) {
            try {
                fabricProject = rootProject.getAllprojects().stream().filter(p -> !p.getName().equals(rootProject.getName())).filter(p -> p.getName().equalsIgnoreCase(fabricConfiguration.getProjectName())).findFirst().get();
                validation.add(true);
            } catch (NoSuchElementException ignored) { }
        }

        if (quiltConfiguration != null) {
            try {
                quiltProject = rootProject.getAllprojects().stream().filter(p -> !p.getName().equals(rootProject.getName())).filter(p -> p.getName().equalsIgnoreCase(quiltConfiguration.getProjectName())).findFirst().get();
                validation.add(true);
            } catch (NoSuchElementException ignored) { }
        }

        if (customConfigurations != null) {
            for (ModFusionerExtension.CustomConfiguration customSettings : customConfigurations) {
                try {
                    customProjects.put(rootProject.getAllprojects().stream().filter(p -> !p.getName().equals(rootProject.getName())).filter(p -> p.getName().equals(customSettings.getProjectName())).findFirst().get(), customSettings);
                    validation.add(true);
                } catch (NoSuchElementException ignored) { }
            }
        }

        // Check that at least 2 projects are defined
        if (validation.size() < 2) {
            if (validation.size() == 1) ModFusionerPlugin.logger.error("Only one project was found. Skipping meldJars task.");
            if (validation.size() == 0) ModFusionerPlugin.logger.error("No projects were found. Skipping meldJars task.");
            return;
        }
        validation.clear();

        // Try to automatically determine the input jar from the projects
        File forgeJar = null;
        File fabricJar = null;
        File quiltJar = null;
        Map<ModFusionerExtension.CustomConfiguration, File> customJars = new HashMap<>();

        if (forgeProject != null && forgeConfiguration != null) {
            forgeJar = getInputFile(forgeConfiguration.getJarLocation(), forgeProject);
        }

        if (fabricProject != null && fabricConfiguration != null) {
            fabricJar = getInputFile(fabricConfiguration.getJarLocation(), fabricProject);
        }

        if (quiltProject != null && quiltConfiguration != null) {
            quiltJar = getInputFile(quiltConfiguration.getJarLocation(), quiltProject);
        }

        for (Map.Entry<Project, ModFusionerExtension.CustomConfiguration> entry : customProjects.entrySet()) {
            File f = getInputFile(entry.getValue().getJarLocation(), entry.getKey());
            if (f != null)
                customJars.put(entry.getValue(), f);
        }

        // Set up the final output jar
        File mergedJar = new File(rootProject.getRootDir(), modFusionerExtension.getOutputDir() + File.separator + modFusionerExtension.getOutJarName());
        if (mergedJar.exists()) FileUtils.forceDelete(mergedJar);
        if (!mergedJar.getParentFile().exists()) mergedJar.getParentFile().mkdirs();

        // Set up the jar merge action
        JarMergeAction mergeAction = JarMergeAction.of(
                customJars,
                modFusionerExtension.getDuplicateRelocations(),
                modFusionerExtension.getGroup(),
                new File(rootProject.getRootDir(), ".gradle" + File.separator + "fusioner"),
                modFusionerExtension.getOutJarName()
        );

        // Forge
        mergeAction.setForgeInput(forgeJar);
        mergeAction.setForgeRelocations(forgeConfiguration == null ? new HashMap<>() : forgeConfiguration.getRelocations());
        mergeAction.setForgeMixins(forgeConfiguration == null ? new ArrayList<>() : forgeConfiguration.getMixins());

        // Fabric
        mergeAction.setFabricInput(fabricJar);
        mergeAction.setFabricRelocations(fabricConfiguration == null ? new HashMap<>() : fabricConfiguration.getRelocations());

        // Quilt
        mergeAction.setQuiltInput(quiltJar);
        mergeAction.setQuiltRelocations(quiltConfiguration == null ? new HashMap<>() : quiltConfiguration.getRelocations());

        // Merge them jars
        Path tempMergedJarPath = mergeAction.mergeJars(false).toPath();

        // Move the merged jar to the specified output directory
        Files.move(tempMergedJarPath, mergedJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.setPosixFilePermissions(mergedJar.toPath(), Constants.filePerms);
        } catch (UnsupportedOperationException | IOException | SecurityException ignored) { }

        // Cleanup
        mergeAction.clean();

        ModFusionerPlugin.logger.lifecycle("Fused jar created in " + (System.currentTimeMillis() - time) / 1000.0 + " seconds.");
    }

    /**
     * Try to determine the input jar of a project
     * @param jarLocation - The user defined jar location
     * @param inProject - The project the file should be for or from
     * @return - The jar file or null
     */
    @Nullable
    private File getInputFile(@Nullable String jarLocation, Project inProject) {
        if (jarLocation != null && !jarLocation.isEmpty()) {
            return new File(inProject.getProjectDir(), jarLocation);
        } else {
            int i = 0;
            for (File file : new File(inProject.getBuildDir(), "libs").listFiles()) {
                if (file.isDirectory()) continue;
                if (FileChecks.isZipFile(file)) {
                    if (file.getName().length() < i || i == 0) {
                        i = file.getName().length();
                        return file;
                    }
                }
            }
        }

        return null;
    }
}
