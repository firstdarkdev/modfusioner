/*
 * This file is part of ModFusioner, licensed under the GNU Lesser General Public License v2.1.
 *
 * This project is based on, and contains code from https://github.com/PacifistMC/Forgix, licensed under the same license.
 * See their license here: https://github.com/PacifistMC/Forgix/blob/main/LICENSE
 *
 * Copyright HypherionSA and Contributors
 * Forgix Code Copyright by their contributors and Ran-Mewo
 */
package com.hypherionmc.modfusioner.actions;

import com.hypherionmc.jarmanager.JarManager;
import com.hypherionmc.jarrelocator.Relocation;
import com.hypherionmc.modfusioner.Constants;
import com.hypherionmc.modfusioner.plugin.FusionerExtension;
import com.hypherionmc.modfusioner.utils.FileTools;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.Deflater;

import static com.hypherionmc.modfusioner.plugin.ModFusionerPlugin.logger;
import static com.hypherionmc.modfusioner.plugin.ModFusionerPlugin.modFusionerExtension;
import static com.hypherionmc.modfusioner.utils.FileTools.*;

/**
 * @author HypherionSA
 * The main logic class of the plugin. This class is responsible for
 * extracting, remapping, de-duplicating and finally merging the input jars
 */
@RequiredArgsConstructor(staticName = "of")
public class JarMergeAction {

    // File Inputs
    @Setter private File forgeInput;
    @Setter private File neoforgeInput;
    @Setter private File fabricInput;
    @Setter private File quiltInput;
    private final Map<FusionerExtension.CustomConfiguration, File> customInputs;

    // Relocations
    @Setter private Map<String, String> forgeRelocations;
    @Setter private Map<String, String> neoforgeRelocations;
    @Setter private Map<String, String> fabricRelocations;
    @Setter private Map<String, String> quiltRelocations;

    // Mixins
    @Setter private List<String> forgeMixins;

    // Custom
    private Map<FusionerExtension.CustomConfiguration, Map<File, File>> customTemps;

    // Relocations
    private final List<String> ignoredPackages;
    private final Map<String, String> ignoredDuplicateRelocations = new HashMap<>();
    private final Map<String, String> removeDuplicateRelocationResources = new HashMap<>();
    private final List<Relocation> relocations = new ArrayList<>();
    JarManager jarManager = JarManager.getInstance();


    // Settings
    private final String group;
    private final File tempDir;
    private final String outJarName;

    /**
     * Start the merge process
     * @param skipIfExists - Should the task be cancelled if an existing merged jar is found
     * @return - The fully merged jar file
     * @throws IOException - Thrown when an IO Exception occurs
     */
    public File mergeJars(boolean skipIfExists) throws IOException {
        jarManager.setCompressionLevel(Deflater.BEST_COMPRESSION);
        File outJar = new File(tempDir, outJarName);
        if (outJar.exists()) {
            if (skipIfExists) return outJar;
            outJar.delete();
        }

        logger.lifecycle("Cleaning output Directory");
        FileTools.createOrReCreate(tempDir);

        // Check if the required input files exists
        if (forgeInput == null && neoforgeInput == null && fabricInput == null && quiltInput == null && customInputs.isEmpty()) {
            throw new IllegalArgumentException("No input jars were provided.");
        }

        if (modFusionerExtension.getForgeConfiguration() != null && !FileTools.exists(forgeInput)) {
            logger.warn("Forge jar does not exist! You can ignore this warning if you are not using forge");
        }

        if (modFusionerExtension.getNeoforgeConfiguration() != null && !FileTools.exists(neoforgeInput)) {
            logger.warn("NeoForge jar does not exist! You can ignore this warning if you are not using neoforge");
        }

        if (modFusionerExtension.getFabricConfiguration() != null && !FileTools.exists(fabricInput)) {
            logger.warn("Fabric jar does not exist! You can ignore this warning if you are not using fabric");
        }

        if (modFusionerExtension.getQuiltConfiguration() != null && !FileTools.exists(quiltInput)) {
            logger.warn("Quilt jar does not exist! You can ignore this warning if you are not using quilt");
        }

        customInputs.forEach((key, value) -> {
            if (!FileTools.exists(value)) {
                logger.warn(key.getProjectName() + " jar does not exist! You can ignore this if you are not using custom configurations");
            }
        });

        // Remap the jar files to match their platform name
        remapJars();

        // Create the temporary processing directories
        File fabricTemp = FileTools.getOrCreate(new File(tempDir, "fabric-temp"));
        File forgeTemp = FileTools.getOrCreate(new File(tempDir, "forge-temp"));
        File neoforgeTemp = FileTools.getOrCreate(new File(tempDir, "neoforge-temp"));
        File quiltTemp = FileTools.getOrCreate(new File(tempDir, "quilt-temp"));

        customTemps = new HashMap<>();
        customInputs.forEach((key, value) -> {
            Map<File, File> temp = new HashMap<>();

            temp.put(value, new File(tempDir, key.getProjectName() + "-temp"));
            FileTools.getOrCreate(new File(tempDir, key.getProjectName() + "-temp"));
            customTemps.put(key, temp);
        });

        // Extract the input jars to their processing directories
        logger.lifecycle("Unpacking input jars");

        if (FileTools.exists(forgeInput)) {
            jarManager.unpackJar(forgeInput, forgeTemp);
        }
        if (FileTools.exists(neoforgeInput)) {
            jarManager.unpackJar(neoforgeInput, neoforgeTemp);
        }
        if (FileTools.exists(fabricInput)) {
            jarManager.unpackJar(fabricInput, fabricTemp);
        }
        if (FileTools.exists(quiltInput)) {
            jarManager.unpackJar(quiltInput, quiltTemp);
        }

        customTemps.forEach((key, value) -> value.forEach((k, v) -> {
            if (FileTools.exists(k)) {
                try {
                    jarManager.unpackJar(k, v);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }));

        File mergedTemp = FileTools.getOrCreate(new File(tempDir, "merged-temp"));
        processManifests(mergedTemp, forgeTemp, neoforgeTemp, fabricTemp, quiltTemp);

        FileTools.moveDirectory(forgeTemp, mergedTemp);
        FileTools.moveDirectory(neoforgeTemp, mergedTemp);
        FileTools.moveDirectory(fabricTemp, mergedTemp);
        FileTools.moveDirectory(quiltTemp, mergedTemp);

        for (Map.Entry<FusionerExtension.CustomConfiguration, Map<File, File>> entry : customTemps.entrySet()) {
            for (Map.Entry<File, File> entry2 : entry.getValue().entrySet()) {
                FileTools.moveDirectory(entry2.getValue(), mergedTemp);
            }
        }

        // Process duplicate packages and resources
        logger.lifecycle("Processing duplicate packages and resources");
        processDuplicatePackages();
        removeDuplicatePackages(mergedTemp);
        removeDuplicateResources(mergedTemp);

        // Clean the output jar if it exists
        FileUtils.deleteQuietly(outJar);

        // Repack the fully processed jars into a single jar
        logger.lifecycle("Fusing jars into single jar");
        jarManager.remapAndPack(mergedTemp, outJar, relocations);

        try {
            Files.setPosixFilePermissions(outJar.toPath(), Constants.filePerms);
        } catch (Exception ignored) {}

        return outJar;
    }

    /**
     * Clean the output directory before the task exists
     * @throws IOException - Thrown if an IO error occurs
     */
    public void clean() throws IOException {
        logger.lifecycle("Finishing up");
        FileUtils.deleteQuietly(tempDir);
    }

    /**
     * ================================================================================================================
     * =                                            Jar Remapping                                                     =
     * ================================================================================================================
     */

    /**
     * Process input jars to relocate them internally to their final package names
     * @throws IOException - Thrown if an IO error occurs
     */
    public void remapJars() throws IOException {
        logger.lifecycle("Start processing input jars");

        remapJar(forgeInput, "forge", forgeRelocations);
        remapJar(neoforgeInput, "neoforge", neoforgeRelocations);
        remapJar(fabricInput, "fabric", fabricRelocations);
        remapJar(quiltInput, "quilt", quiltRelocations);

        for (Map.Entry<FusionerExtension.CustomConfiguration, File> entry : customInputs.entrySet()) {
            if (FileTools.exists(entry.getValue())) {
                remapCustomJar(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Remap a Forge/Fabric/Quilt Jar
     * @param jarFile - The input jar
     * @param target - The identifier of the package names
     * @param relocations - List of packages to be moved around
     * @throws IOException - Thrown if an io exception occurs
     */
    private void remapJar(File jarFile, String target, Map<String, String> relocations) throws IOException {
        if (FileTools.exists(jarFile)) {
            File remappedJar = FileTools.createOrReCreateF(new File(tempDir, "temp" + target + "InMerging.jar"));

            List<Relocation> jarRelocations = new ArrayList<>();
            jarRelocations.add(new Relocation(group, target + "." + group));
            if (relocations != null)
                jarRelocations.addAll(relocations.entrySet().stream().map(entry -> new Relocation(entry.getKey(), entry.getValue())).collect(ArrayList::new, ArrayList::add, ArrayList::addAll));

            AtomicReference<String> architectury = new AtomicReference<>();
            architectury.set(null);

            JarFile jar = new JarFile(jarFile);
            jar.stream().forEach(jarEntry -> {
                if (jarEntry.isDirectory()) {
                    if (jarEntry.getName().startsWith("architectury_inject")) {
                        architectury.set(jarEntry.getName());
                    }
                } else {
                    String firstDirectory = getFirstDirectory(jarEntry.getName());
                    if (firstDirectory.startsWith("architectury_inject")) {
                        architectury.set(firstDirectory);
                    }
                }
            });
            jar.close();

            if (architectury.get() != null) {
                jarRelocations.add(new Relocation(architectury.get(), target + "." + architectury.get()));
            }

            jarManager.remapJar(jarFile, remappedJar, jarRelocations);

            switch (target) {
                case "forge":
                    forgeInput = remappedJar;
                    break;
                case "neoforge":
                    neoforgeInput = remappedJar;
                    break;
                case "fabric":
                    fabricInput = remappedJar;
                    break;
                case "quilt":
                    quiltInput = remappedJar;
                    break;
            }
        }
    }

    /**
     * Remap a Custom Jar
     * @param configuration - The configuration of the custom package
     * @param jarFile - The input jar of the custom project to be processed
     * @throws IOException - Thrown if an io exception occurs
     */
    private void remapCustomJar(FusionerExtension.CustomConfiguration configuration, File jarFile) throws IOException {
        String name = configuration.getProjectName();
        File remappedJar = FileTools.createOrReCreateF(new File(tempDir, "tempCustomInMerging_" + name + ".jar"));

        List<Relocation> customRelocations = new ArrayList<>();
        customRelocations.add(new Relocation(group, name + "." + group));
        if (configuration.getRelocations() != null)
            customRelocations.addAll(configuration.getRelocations().entrySet().stream().map(entry -> new Relocation(entry.getKey(), entry.getValue())).collect(ArrayList::new, ArrayList::add, ArrayList::addAll));

        AtomicReference<String> architectury = new AtomicReference<>();
        architectury.set(null);

        JarFile jar = new JarFile(jarFile);
        jar.stream().forEach(jarEntry -> {
            if (jarEntry.isDirectory()) {
                if (jarEntry.getName().startsWith("architectury_inject")) {
                    architectury.set(jarEntry.getName());
                }
            } else {
                String firstDirectory = getFirstDirectory(jarEntry.getName());
                if (firstDirectory.startsWith("architectury_inject")) {
                    architectury.set(firstDirectory);
                }
            }
        });
        jar.close();

        if (architectury.get() != null) {
            customRelocations.add(new Relocation(architectury.get(), name + "." + architectury.get()));
        }

        jarManager.remapJar(jarFile, remappedJar, customRelocations);
        customInputs.replace(configuration, jarFile, remappedJar);
    }

    /**
     * Process resource files from unpacked jars to remap them to their new package names
     * @param forgeTemps - The forge processing directory
     * @param neoforgeTemps - The neoforge processing directory
     * @param fabricTemps - The fabric processing directory
     * @param quiltTemps - The quilt processing directory
     * @throws IOException - Thrown if an IO error occurs
     */
    private void remapResources(File forgeTemps, File neoforgeTemps, File fabricTemps, File quiltTemps) throws IOException {
        logger.lifecycle("Start Remapping Resources");
        remapJarResources(forgeInput, "forge", forgeTemps, forgeRelocations);
        remapJarResources(neoforgeInput, "neoforge", neoforgeTemps, neoforgeRelocations);
        remapJarResources(fabricInput, "fabric", fabricTemps, fabricRelocations);
        remapJarResources(quiltInput, "quilt", quiltTemps, quiltRelocations);

        for (Map.Entry<FusionerExtension.CustomConfiguration, Map<File, File>> entry : customTemps.entrySet()) {
            for (Map.Entry<File, File> entry2 : entry.getValue().entrySet()) {
                if (entry2.getKey() != null && entry2.getKey().exists()) {
                    File customTemps = entry2.getValue();
                    String name = entry.getKey().getProjectName();
                    remapJarResources(null, name, customTemps, entry.getKey().getRelocations());
                }
            }
        }
    }

    /**
     * Remap resource files from jar. Used to remove duplicate code from {@link JarMergeAction#remapResources(File, File, File, File)}
     * @param jar - The jar file being processed
     * @param identifier - The group identifier of the packages
     * @param workingDir - The processing directory
     * @param relocations - List of packages that have been relocated
     * @throws IOException - Thrown if an IO error occurs
     */
    private void remapJarResources(File jar, String identifier, File workingDir, Map<String, String> relocations) throws IOException {
        if (jar != null && !jar.exists())
            return;

        if (relocations == null) relocations = new HashMap<>();
        for (File file : embeddedJars(workingDir)) {
            File remappedFile = new File(file.getParentFile(), identifier + "-" + file.getName());
            relocations.put(file.getName(), remappedFile.getName());
            file.renameTo(remappedFile);
        }

        for (File file : getPlatformServices(workingDir, group)) {
            File remappedFile = new File(file.getParentFile(), identifier + "." + file.getName());
            relocations.put(file.getName(), remappedFile.getName());
            file.renameTo(remappedFile);
        }

        if (identifier.equalsIgnoreCase("forge"))
            forgeMixins = new ArrayList<>();

        for (File file : getMixins(workingDir, !identifier.equalsIgnoreCase("forge"))) {
            File remappedFile = new File(file.getParentFile(), identifier + "-" + file.getName());
            relocations.put(file.getName(), remappedFile.getName());
            file.renameTo(remappedFile);

            if (identifier.equalsIgnoreCase("forge"))
                forgeMixins.add(remappedFile.getName());
        }

        if (!identifier.equalsIgnoreCase("forge")) {
            for (File file : getAccessWideners(workingDir)) {
                File remappedFile = new File(file.getParentFile(), identifier + "-" + file.getName());
                relocations.put(file.getName(), remappedFile.getName());
                file.renameTo(remappedFile);
            }
        }

        for (File file : getRefmaps(workingDir)) {
            File remappedFile = new File(file.getParentFile(), identifier + "-" + file.getName());
            relocations.put(file.getName(), remappedFile.getName());
            file.renameTo(remappedFile);
        }

        relocations.put(group, identifier + "." + group);
        relocations.put(group.replace(".", "/"), identifier + "/" + group.replace(".", "/"));

        for (File file : getTextFiles(workingDir)) {
            List<String> lines = FileUtils.readLines(file, StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();

            for (String line : lines) {
                for (HashMap.Entry<String, String> entry : relocations.entrySet()) {
                    line = line.replace(entry.getKey(), entry.getValue());
                }
                sb.append(line).append("\n");
            }
            FileUtils.write(file, sb.toString().trim(), StandardCharsets.UTF_8);
        }
    }

    /**
     * ================================================================================================================
     * =                                         Manifest Merging                                                     =
     * ================================================================================================================
     */

    /**
     * Process the manifest files from all the input jars and combine them into one
     * @param mergedTemp - The processing directory
     * @param forgeTemp - The forge processing directory
     * @param neoforgeTemp - The neoforge processing directory
     * @param fabricTemp - The fabric processing directory
     * @param quiltTemp - The quilt processing directory
     * @throws IOException - Thrown if an IO error occurs
     */
    public void processManifests(File mergedTemp, File forgeTemp, File neoforgeTemp, File fabricTemp, File quiltTemp) throws IOException {
        Manifest mergedManifest = new Manifest();
        Manifest forgeManifest = new Manifest();
        Manifest neoforgeManifest = new Manifest();
        Manifest fabricManifest = new Manifest();
        Manifest quiltManifest = new Manifest();
        List<Manifest> customManifests = new ArrayList<>();

        FileInputStream fileInputStream = null;
        if (FileTools.exists(forgeInput)) forgeManifest.read(fileInputStream = new FileInputStream(new File(forgeTemp, "META-INF/MANIFEST.MF")));
        if (fileInputStream != null) fileInputStream.close();
        if (FileTools.exists(neoforgeInput)) neoforgeManifest.read(fileInputStream = new FileInputStream(new File(neoforgeTemp, "META-INF/MANIFEST.MF")));
        if (fileInputStream != null) fileInputStream.close();
        if (FileTools.exists(fabricInput)) fabricManifest.read(fileInputStream = new FileInputStream(new File(fabricTemp, "META-INF/MANIFEST.MF")));
        if (fileInputStream != null) fileInputStream.close();
        if (FileTools.exists(quiltInput)) quiltManifest.read(fileInputStream = new FileInputStream(new File(quiltTemp, "META-INF/MANIFEST.MF")));
        if (fileInputStream != null) fileInputStream.close();

        for (Map.Entry<FusionerExtension.CustomConfiguration, Map<File, File>> entry : customTemps.entrySet()) {
            for (Map.Entry<File, File> entry2 : entry.getValue().entrySet()) {
                Manifest manifest = new Manifest();
                if (FileTools.exists(entry2.getKey())) {
                    manifest.read(fileInputStream = new FileInputStream(new File(entry2.getValue(), "META-INF/MANIFEST.MF")));
                    customManifests.add(manifest);
                }
                if (fileInputStream != null) fileInputStream.close();
            }
        }

        forgeManifest.getMainAttributes().forEach((key, value) -> mergedManifest.getMainAttributes().putValue(key.toString(), value.toString()));
        neoforgeManifest.getMainAttributes().forEach((key, value) -> mergedManifest.getMainAttributes().putValue(key.toString(), value.toString()));
        fabricManifest.getMainAttributes().forEach((key, value) -> mergedManifest.getMainAttributes().putValue(key.toString(), value.toString()));
        quiltManifest.getMainAttributes().forEach((key, value) -> mergedManifest.getMainAttributes().putValue(key.toString(), value.toString()));

        for (Manifest manifest : customManifests) {
            manifest.getMainAttributes().forEach((key, value) -> mergedManifest.getMainAttributes().putValue(key.toString(), value.toString()));
        }

        if (mergedManifest.getMainAttributes().getValue("MixinConfigs") != null) {
            String value = mergedManifest.getMainAttributes().getValue("MixinConfigs");
            String[] mixins;
            List<String> remappedMixin = new ArrayList<>();

            if (value.contains(",")) {
                mixins = value.split(",");
            } else {
                mixins = new String[]{value};
            }

            for (String mixin : mixins) {
                remappedMixin.add("forge-" + mixin);
            }

            mergedManifest.getMainAttributes().putValue("MixinConfigs", String.join(",", remappedMixin));
        }

        if (this.forgeMixins != null) {
            List<String> newForgeMixins = new ArrayList<>();
            for (String mixin : this.forgeMixins) {
                newForgeMixins.add("forge-" + mixin);
            }
            this.forgeMixins = newForgeMixins;
            if (!forgeMixins.isEmpty()) mergedManifest.getMainAttributes().putValue("MixinConfigs", String.join(",", this.forgeMixins));
        }

        remapResources(forgeTemp, neoforgeTemp, fabricTemp, quiltTemp);

        if (this.forgeMixins != null && mergedManifest.getMainAttributes().getValue("MixinConfigs") == null) {
            logger.debug("Couldn't detect forge mixins. You can ignore this if you are not using mixins with forge.\n" +
                    "If this is an issue then you can configure mixins manually\n" +
                    "Though we'll try to detect them automatically.\n");
            if (!forgeMixins.isEmpty()) {
                logger.debug("Detected forge mixins: " + String.join(",", this.forgeMixins) + "\n");
                mergedManifest.getMainAttributes().putValue("MixinConfigs", String.join(",", this.forgeMixins));
            }
        }

        // TODO Manifest Version
        //mergedManifest.getMainAttributes().putValue(manifestVersionKey, version);

        if (FileTools.exists(forgeInput)) new File(forgeTemp, "META-INF/MANIFEST.MF").delete();
        if (FileTools.exists(neoforgeInput)) new File(neoforgeTemp, "META-INF/MANIFEST.MF").delete();
        if (FileTools.exists(fabricInput)) new File(fabricTemp, "META-INF/MANIFEST.MF").delete();
        if (FileTools.exists(quiltInput)) new File(quiltTemp, "META-INF/MANIFEST.MF").delete();

        for (Map.Entry<FusionerExtension.CustomConfiguration, Map<File, File>> entry : customTemps.entrySet()) {
            for (Map.Entry<File, File> entry2 : entry.getValue().entrySet()) {
                if (FileTools.exists(entry2.getKey())) new File(entry2.getValue(), "META-INF/MANIFEST.MF").delete();
            }
        }

        new File(FileTools.getOrCreate(new File(mergedTemp, "META-INF")), "MANIFEST.MF").createNewFile();
        FileOutputStream outputStream = new FileOutputStream(new File(mergedTemp, "META-INF/MANIFEST.MF"));
        mergedManifest.write(outputStream);
        outputStream.close();
    }

    /**
     * ================================================================================================================
     * =                                    Duplicate Package Processing                                              =
     * ================================================================================================================
     */

    /**
     * Build a list of duplicate packages that need to be removed from the final jar
     */
    private void processDuplicatePackages() {
        if (ignoredPackages != null) {
            for (String duplicate : ignoredPackages) {
                String duplicatePath = duplicate.replace(".", "/");

                if (FileTools.exists(forgeInput)) {
                    ignoredDuplicateRelocations.put("forge." + duplicate, duplicate);
                    removeDuplicateRelocationResources.put("forge/" + duplicatePath, duplicatePath);
                }

                if (FileTools.exists(neoforgeInput)) {
                    ignoredDuplicateRelocations.put("neoforge." + duplicate, duplicate);
                    removeDuplicateRelocationResources.put("neoforge/" + duplicatePath, duplicatePath);
                }

                if (FileTools.exists(fabricInput)) {
                    ignoredDuplicateRelocations.put("fabric." + duplicate, duplicate);
                    removeDuplicateRelocationResources.put("fabric/" + duplicatePath, duplicatePath);
                }

                if (FileTools.exists(quiltInput)) {
                    ignoredDuplicateRelocations.put("quilt." + duplicate, duplicate);
                    removeDuplicateRelocationResources.put("quilt/" + duplicatePath, duplicatePath);
                }

                for (Map.Entry<FusionerExtension.CustomConfiguration, Map<File, File>> entry : customTemps.entrySet()) {
                    for (Map.Entry<File, File> entry2 : entry.getValue().entrySet()) {
                        if (FileTools.exists(entry2.getKey())) {
                            String name = entry.getKey().getProjectName();
                            ignoredDuplicateRelocations.put(name + "." + duplicate, duplicate);
                            removeDuplicateRelocationResources.put(name + "/" + duplicatePath, duplicatePath);
                        }
                    }
                }
            }

            removeDuplicateRelocationResources.putAll(ignoredDuplicateRelocations);
        }
    }

    /**
     * Relocate duplicate packages from their original location, to a single location
     * @param mergedTemps - The processing directory
     * @throws IOException - Thrown if an IO exception occurs
     */
    private void removeDuplicatePackages(File mergedTemps) throws IOException {
        for (Map.Entry<String, String> entry : ignoredDuplicateRelocations.entrySet()) {
            File baseFile = new File(mergedTemps, entry.getKey().replace(".", "/") + "/");
            String name = entry.getValue().replace(".", "/") + "/";
            File outFile = new File(mergedTemps, name);

            if (outFile.isDirectory())
                outFile.mkdirs();

            FileTools.moveDirectory(baseFile, outFile);
            relocations.add(new Relocation(entry.getKey(), entry.getValue()));
        }
    }

    /**
     * Remove duplicate resources files from extracted jars
     * @param mergedTemps - The processing directory
     * @throws IOException - Thrown if an IO error occurs
     */
    public void removeDuplicateResources(File mergedTemps) throws IOException {
        if (ignoredPackages != null) {
            for (File file : getTextFiles(mergedTemps)) {
                List<String> lines = FileUtils.readLines(file, StandardCharsets.UTF_8);
                StringBuilder sb = new StringBuilder();

                for (String line : lines) {
                    for (HashMap.Entry<String, String> entry : removeDuplicateRelocationResources.entrySet()) {
                        line = line.replace(entry.getKey(), entry.getValue());
                    }
                    sb.append(line).append("\n");
                }
                FileUtils.write(file, sb.toString().trim() + "\n", StandardCharsets.UTF_8);
            }
        }
    }
}
