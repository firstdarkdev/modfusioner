/*
 * This file is part of ModFusioner, licensed under the GNU Lesser General Public License v2.1.
 *
 * This project is based on, and contains code from https://github.com/PacifistMC/Forgix, licensed under the same license.
 * See their license here: https://github.com/PacifistMC/Forgix/blob/main/LICENSE
 *
 * Copyright HypherionSA and Contributors
 * Forgix Code Copyright by their contributors and Ran-Mewo
 */
package com.hypherionmc.modfusioner.task;

import com.hypherionmc.modfusioner.Constants;
import com.hypherionmc.modfusioner.actions.JarMergeAction;
import com.hypherionmc.modfusioner.plugin.FusionerExtension;
import com.hypherionmc.modfusioner.plugin.ModFusionerPlugin;
import com.hypherionmc.modfusioner.utils.FileChecks;
import com.hypherionmc.modfusioner.utils.FileTools;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.tasks.WorkResults;
import org.gradle.jvm.tasks.Jar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.hypherionmc.modfusioner.plugin.ModFusionerPlugin.modFusionerExtension;
import static com.hypherionmc.modfusioner.plugin.ModFusionerPlugin.rootProject;

/**
 * @author HypherionSA
 * The main task of the plugin
 */
public class JarFuseTask extends Jar {

    // Fixed values
    private final File mergedJar;
    private static final AtomicBoolean hasRun = new AtomicBoolean(false);

    public JarFuseTask() {
        // Set task default values from extension
        getArchiveBaseName().set(modFusionerExtension.getMergedJarName());
        getArchiveVersion().set(modFusionerExtension.getJarVersion());
        getDestinationDirectory().set(getProject().file(modFusionerExtension.getOutputDirectory()));

        // We don't allow custom input files, when the user defines their own task
        getInputs().files();

        // Only allow the task to run once per cycle
        getOutputs().upToDateWhen(spec -> hasRun.get());

        // Set output file
        mergedJar = new File(getDestinationDirectory().get().getAsFile(), getArchiveFileName().get());
        getOutputs().file(mergedJar);
    }

    /**
     * Main task logic
     * @throws IOException - Thrown when an IO error occurs
     */
    void fuseJars() throws IOException {
        long time = System.currentTimeMillis();

        ModFusionerPlugin.logger.lifecycle("Start Fusing Jars");

        // Get settings from extension
        FusionerExtension.ForgeConfiguration forgeConfiguration = modFusionerExtension.getForgeConfiguration();
        FusionerExtension.NeoForgeConfiguration neoforgeConfiguration = modFusionerExtension.getNeoforgeConfiguration();
        FusionerExtension.FabricConfiguration fabricConfiguration = modFusionerExtension.getFabricConfiguration();
        FusionerExtension.QuiltConfiguration quiltConfiguration = modFusionerExtension.getQuiltConfiguration();

        List<FusionerExtension.CustomConfiguration> customConfigurations = modFusionerExtension.getCustomConfigurations();

        // Try to resolve the projects specific in the extension config
        Project forgeProject = null;
        Project neoforgeProject = null;
        Project fabricProject = null;
        Project quiltProject = null;
        Map<Project, FusionerExtension.CustomConfiguration> customProjects = new HashMap<>();
        List<Boolean> validation = new ArrayList<>();

        if (forgeConfiguration != null) {
            try {
                forgeProject = rootProject.getAllprojects().stream().filter(p -> !p.getName().equals(rootProject.getName())).filter(p -> p.getName().equalsIgnoreCase(forgeConfiguration.getProjectName())).findFirst().get();
                validation.add(true);
            } catch (NoSuchElementException ignored) { }
        }

        if (neoforgeConfiguration != null) {
            try {
                neoforgeProject = rootProject.getAllprojects().stream().filter(p -> !p.getName().equals(rootProject.getName())).filter(p -> p.getName().equalsIgnoreCase(neoforgeConfiguration.getProjectName())).findFirst().get();
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
            for (FusionerExtension.CustomConfiguration customSettings : customConfigurations) {
                try {
                    customProjects.put(rootProject.getAllprojects().stream().filter(p -> !p.getName().equals(rootProject.getName())).filter(p -> p.getName().equals(customSettings.getProjectName())).findFirst().get(), customSettings);
                    validation.add(true);
                } catch (NoSuchElementException ignored) { }
            }
        }

        // Check that at least 2 projects are defined
        if (validation.size() < 2) {
            if (validation.size() == 1) ModFusionerPlugin.logger.error("Only one project was found. Skipping fusejars task.");
            if (validation.size() == 0) ModFusionerPlugin.logger.error("No projects were found. Skipping fusejars task.");
            return;
        }
        validation.clear();

        // Try to automatically determine the input jar from the projects
        File forgeJar = null;
        File neoforgeJar = null;
        File fabricJar = null;
        File quiltJar = null;
        Map<FusionerExtension.CustomConfiguration, File> customJars = new HashMap<>();

        if (forgeProject != null && forgeConfiguration != null) {
            forgeJar = getInputFile(forgeConfiguration.getInputFile(), forgeConfiguration.getInputTaskName(), forgeProject);
        }

        if (neoforgeProject != null && neoforgeConfiguration != null) {
            neoforgeJar = getInputFile(neoforgeConfiguration.getInputFile(), neoforgeConfiguration.getInputTaskName(), neoforgeProject);
        }

        if (fabricProject != null && fabricConfiguration != null) {
            fabricJar = getInputFile(fabricConfiguration.getInputFile(), fabricConfiguration.getInputTaskName(), fabricProject);
        }

        if (quiltProject != null && quiltConfiguration != null) {
            quiltJar = getInputFile(quiltConfiguration.getInputFile(), quiltConfiguration.getInputTaskName(), quiltProject);
        }

        for (Map.Entry<Project, FusionerExtension.CustomConfiguration> entry : customProjects.entrySet()) {
            File f = getInputFile(entry.getValue().getInputFile(), entry.getValue().getInputTaskName(), entry.getKey());
            if (f != null)
                customJars.put(entry.getValue(), f);
        }

        // Set up the final output jar
        if (mergedJar.exists()) FileUtils.forceDelete(mergedJar);
        if (!mergedJar.getParentFile().exists()) mergedJar.getParentFile().mkdirs();

        // Set up the jar merge action
        JarMergeAction mergeAction = JarMergeAction.of(
                customJars,
                modFusionerExtension.getDuplicateRelocations(),
                modFusionerExtension.getPackageGroup(),
                new File(rootProject.getRootDir(), ".gradle" + File.separator + "fusioner"),
                getArchiveFileName().get()
        );

        // Forge
        mergeAction.setForgeInput(forgeJar);
        mergeAction.setForgeRelocations(forgeConfiguration == null ? new HashMap<>() : forgeConfiguration.getRelocations());
        mergeAction.setForgeMixins(forgeConfiguration == null ? new ArrayList<>() : forgeConfiguration.getMixins());

        // NeoForge
        mergeAction.setNeoforgeInput(neoforgeJar);
        mergeAction.setNeoforgeRelocations(neoforgeConfiguration == null ? new HashMap<>() : neoforgeConfiguration.getRelocations());

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
        hasRun.set(true);
    }

    /**
     * Run the main task logic and copy the files to the correct locations
     * @return - Just returns true to say the task executed
     */
    @Override
    protected @NotNull CopyAction createCopyAction() {
        return copyActionProcessingStream -> {
            copyActionProcessingStream.process(fileCopyDetailsInternal -> {
                if (!hasRun.get())
                    try {
                        fuseJars();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
            });

            return WorkResults.didWork(true);
        };
    }

    /**
     * Try to determine the input jar of a project
     * @param jarLocation - The user defined jar location
     * @param inProject - The project the file should be for or from
     * @return - The jar file or null
     */
    @Nullable
    private File getInputFile(@Nullable String jarLocation, String inputTaskName, Project inProject) {
        if (jarLocation != null && !jarLocation.isEmpty()) {
            return new File(inProject.getProjectDir(), jarLocation);
        } else if (inputTaskName != null && !inputTaskName.isEmpty()) {
          return FileTools.resolveFile(inProject, inputTaskName);
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
