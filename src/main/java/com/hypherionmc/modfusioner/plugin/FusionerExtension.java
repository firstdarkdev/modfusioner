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

import groovy.lang.Closure;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

public class FusionerExtension {

    // Group, or package names that will be used for the final jar
    @Getter
    @Setter
    String packageGroup;

    // The name of the final jar
    @Getter @Setter
    String mergedJarName;

    // The name of the final jar
    @Getter @Setter
    String jarVersion;

    // Duplicate packages that will be de-duplicated upon merge
    @Getter
    List<String> duplicateRelocations;

    // The output directory for the merged jar
    @Getter @Setter
    String outputDirectory;

    // Forge Project Configuration
    @Getter @Setter
    FusionerExtension.ForgeConfiguration forgeConfiguration;

    // Fabric Project Configuration
    @Getter @Setter
    FusionerExtension.FabricConfiguration fabricConfiguration;

    // Quilt Project Configuration
    @Getter @Setter
    FusionerExtension.QuiltConfiguration quiltConfiguration;

    // Custom Project Configurations
    @Getter
    List<FusionerExtension.CustomConfiguration> customConfigurations = new ArrayList<>();

    /**
     * Main extension entry point
     */
    public FusionerExtension() {
        if (packageGroup == null || packageGroup.isEmpty()) {
            if (ModFusionerPlugin.rootProject.hasProperty("group") && ModFusionerPlugin.rootProject.property("group") != null) {
                packageGroup = ModFusionerPlugin.rootProject.property("group").toString();
            } else {
                ModFusionerPlugin.logger.error("\"group\" is not defined and cannot be set automatically");
            }
        }

        if (mergedJarName == null || mergedJarName.isEmpty()) {
            mergedJarName = "MergedJar";
        }

        if (jarVersion == null || jarVersion.isEmpty()) {
            if (ModFusionerPlugin.rootProject.hasProperty("version") && ModFusionerPlugin.rootProject.property("version") != null) {
                jarVersion = ModFusionerPlugin.rootProject.property("version").toString();
            } else {
                jarVersion = "1.0";
            }
        }

        if (outputDirectory == null || outputDirectory.isEmpty())
            outputDirectory = "artifacts/fused";
    }

    /**
     * Add a duplicate package to be de-duplicated
     * @param duplicate - The package name. For example: com.mymod.mylibrary
     */
    public void relocateDuplicate(String duplicate) {
        if (duplicateRelocations == null) duplicateRelocations = new ArrayList<>();
        duplicateRelocations.add(duplicate);
    }

    /**
     * Add duplicate packages to be de-duplicated
     * @param duplicates - List of package names. For example: ["com.mymod.mylibrary", "com.google.gson"]
     */
    public void relocateDuplicates(List<String> duplicates) {
        if (duplicateRelocations == null) duplicateRelocations = new ArrayList<>();
        duplicateRelocations.addAll(duplicates);
    }

    /**
     * Set up the forge project configurations
     */
    public FusionerExtension.ForgeConfiguration forge(Closure<FusionerExtension.ForgeConfiguration> closure) {
        forgeConfiguration = new FusionerExtension.ForgeConfiguration();
        ModFusionerPlugin.rootProject.configure(forgeConfiguration, closure);
        return forgeConfiguration;
    }

    /**
     * Set up the fabric project configurations
     */
    public FusionerExtension.FabricConfiguration fabric(Closure<FusionerExtension.FabricConfiguration> closure) {
        fabricConfiguration = new FusionerExtension.FabricConfiguration();
        ModFusionerPlugin.rootProject.configure(fabricConfiguration, closure);
        return fabricConfiguration;
    }

    /**
     * Set up the quilt project configurations
     */
    public FusionerExtension.QuiltConfiguration quilt(Closure<FusionerExtension.QuiltConfiguration> closure) {
        quiltConfiguration = new FusionerExtension.QuiltConfiguration();
        ModFusionerPlugin.rootProject.configure(quiltConfiguration, closure);
        return quiltConfiguration;
    }

    /**
     * Set up custom project configurations
     */
    public FusionerExtension.CustomConfiguration custom(Closure<FusionerExtension.CustomConfiguration> closure) {
        FusionerExtension.CustomConfiguration customConfiguration = new FusionerExtension.CustomConfiguration();
        ModFusionerPlugin.rootProject.configure(customConfiguration, closure);

        if (customConfiguration.getProjectName() == null || customConfiguration.getProjectName().isEmpty()) {
            throw new IllegalStateException("Custom project configurations need to specify a \"projectName\"");
        }
        customConfigurations.add(customConfiguration);
        return customConfiguration;
    }

    /**
     * Forge Configuration Structure
     */
    public static class ForgeConfiguration {

        // The name of the gradle module that contains the forge code
        @Getter @Setter
        public String projectName = "forge";

        // The file that will be used as the input
        @Getter @Setter
        String inputFile;

        // The name of the task to run to get the input file
        @Getter @Setter
        String inputTaskName;

        // Packages that should be relocated, instead of duplicated
        @Getter
        Map<String, String> relocations = new LinkedHashMap<>();

        // Forge Mixin Configs
        @Getter
        List<String> mixins = new ArrayList<>();

        /**
         * Add a package to relocate, instead of duplicating
         * @param from - The original name of the package. For example: com.google.gson
         * @param to - The new name of the package. For example: forge.com.google.gson
         */
        public void addRelocate(String from, String to) {
            this.relocations.put(from, to);
        }

        /**
         * Add a mixin config file
         * @param mixin - The name of the mixin config file
         */
        public void mixin(String mixin) {
            this.mixins.add(mixin);
        }
    }

    /**
     * Fabric project configuration
     */
    public static class FabricConfiguration {

        // The name of the gradle module that contains the fabric code
        @Getter @Setter
        String projectName = "fabric";

        // The file that will be used as the input
        @Getter @Setter
        String inputFile;

        // The name of the task to run to get the input file
        @Getter @Setter
        String inputTaskName;

        // Packages that should be relocated, instead of duplicated
        @Getter
        Map<String, String> relocations = new HashMap<>();

        /**
         * Add a package to relocate, instead of duplicating
         * @param from - The original name of the package. For example: com.google.gson
         * @param to - The new name of the package. For example: forge.com.google.gson
         */
        public void addRelocate(String from, String to) {
            this.relocations.put(from, to);
        }
    }

    /**
     * Quilt project configuration
     */
    public static class QuiltConfiguration {

        // The name of the gradle module that contains the quilt code
        @Getter @Setter
        String projectName = "quilt";

        // The file that will be used as the input
        @Getter @Setter
        String inputFile;

        // The name of the task to run to get the input file
        @Getter @Setter
        String inputTaskName;

        // Packages that should be relocated, instead of duplicated
        @Getter
        Map<String, String> relocations = new HashMap<>();

        /**
         * Add a package to relocate, instead of duplicating
         * @param from - The original name of the package. For example: com.google.gson
         * @param to - The new name of the package. For example: forge.com.google.gson
         */
        public void addRelocate(String from, String to) {
            this.relocations.put(from, to);
        }
    }

    /**
     * Custom project configuration
     */
    public static class CustomConfiguration {

        // The name of the gradle module that contains the custom code
        @Getter @Setter
        String projectName;

        // The file that will be used as the input
        @Getter @Setter
        String inputFile;

        // The name of the task to run to get the input file
        @Getter @Setter
        String inputTaskName;

        // Packages that should be relocated, instead of duplicated
        @Getter
        Map<String, String> relocations = new HashMap<>();

        /**
         * Add a package to relocate, instead of duplicating
         * @param from - The original name of the package. For example: com.google.gson
         * @param to - The new name of the package. For example: forge.com.google.gson
         */
        public void addRelocation(String from, String to) {
            this.relocations.put(from, to);
        }
    }

}
