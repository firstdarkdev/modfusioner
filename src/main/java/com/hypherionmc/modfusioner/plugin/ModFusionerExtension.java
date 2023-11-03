package com.hypherionmc.modfusioner.plugin;

import groovy.lang.Closure;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

/**
 * @author HypherionSA
 * Main Plugin Extension allowing users to configure the plugin
 */
public class ModFusionerExtension {

    // Group, or package names that will be used for the final jar
    @Getter @Setter
    String group;

    // The name of the final jar
    @Getter @Setter
    String outJarName;

    // Duplicate packages that fill be de-duplicated upon merge
    @Getter
    List<String> duplicateRelocations;

    // The output directory for the merged jar
    @Getter @Setter
    String outputDir = "fused";

    // Forge Project Configuration
    @Getter @Setter
    ForgeConfiguration forgeConfiguration;

    // Fabric Project Configuration
    @Getter @Setter
    FabricConfiguration fabricConfiguration;

    // Quilt Project Configuration
    @Getter @Setter
    QuiltConfiguration quiltConfiguration;

    // Custom Project Configurations
    @Getter
    List<CustomConfiguration> customConfigurations = new ArrayList<>();

    /**
     * Main extension entry point
     */
    public ModFusionerExtension() {
        if (group == null || group.isEmpty()) {
            if (ModFusionerPlugin.rootProject.hasProperty("group") && ModFusionerPlugin.rootProject.property("group") != null) {
                group = ModFusionerPlugin.rootProject.property("group").toString();
            } else {
                ModFusionerPlugin.logger.error("\"group\" is not defined and cannot be set automatically");
            }
        }
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
    public ForgeConfiguration forge(Closure<ForgeConfiguration> closure) {
        forgeConfiguration = new ForgeConfiguration();
        ModFusionerPlugin.rootProject.configure(forgeConfiguration, closure);
        return forgeConfiguration;
    }

    /**
     * Set up the fabric project configurations
     */
    public FabricConfiguration fabric(Closure<FabricConfiguration> closure) {
        fabricConfiguration = new FabricConfiguration();
        ModFusionerPlugin.rootProject.configure(fabricConfiguration, closure);
        return fabricConfiguration;
    }

    /**
     * Set up the quilt project configurations
     */
    public QuiltConfiguration quilt(Closure<QuiltConfiguration> closure) {
        quiltConfiguration = new QuiltConfiguration();
        ModFusionerPlugin.rootProject.configure(quiltConfiguration, closure);
        return quiltConfiguration;
    }

    /**
     * Set up custom project configurations
     */
    public CustomConfiguration custom(Closure<CustomConfiguration> closure) {
        CustomConfiguration customConfiguration = new CustomConfiguration();
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
        String jarLocation;

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
        String jarLocation;

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
        String jarLocation;

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
        String jarLocation;

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
