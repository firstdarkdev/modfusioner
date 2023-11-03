package com.hypherionmc.modfusioner.utils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.CopyOption;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

import static org.apache.commons.io.FileUtils.*;

/**
 * @author HypherionSA
 * Utility class to make working with files easier
 */
public class FileTools {

    // Name of the META-INF directory inside the mods
    private static final String META_DIR = "META-INF";

    // Fabric folder name of "shaded" jars
    private static final String JARS_DIR = "jars";

    // Forge folder name of Jar in Jar jars
    private static final String JARJAR_DIR = "jarjar";

    // Services folder name
    private static final String SERVICES_DIR = "services";

    /**
     * Test to see if input file is not null, and exists on the drive
     * @param file - The file to test
     * @return - True if not null and exists on drive
     */
    public static boolean exists(File file) {
        return file != null && file.exists();
    }

    /**
     * Create a directory if it doesn't exist
     * @param file - The directory to create
     * @return - The "now existent" directory
     */
    public static File getOrCreate(File file) {
        if (!file.exists())
            file.mkdirs();

        return file;
    }

    /**
     * Move a directory from one location to another
     * @param sourceDir - The directory to copy from
     * @param outDir - The directory to copy to
     * @throws IOException - Thrown if an IO error occurs
     */
    public static void moveDirectory(File sourceDir, File outDir) throws IOException {
        if (!exists(sourceDir))
            return;

        File[] files = sourceDir.listFiles();
        if (files == null)
            return;

        for (File f : files) {
            File outPath = new File(outDir, f.getName());

            if (f.isDirectory()) {
                moveDirectoryInternal(f, outPath);
            }

            if (f.isFile()) {
                moveFileInternal(f, outPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * Copied from Apache Commons, with the "Directory Must Not Exist" check removed
     * @param srcDir - The source directory
     * @param destDir - The destination directory
     * @throws IOException - Thrown if an IO error occurs
     */
    private static void moveDirectoryInternal(final File srcDir, final File destDir) throws IOException {
        validateMoveParameters(srcDir, destDir);
        requireDirectory(srcDir, "srcDir");

        if (!srcDir.renameTo(destDir)) {
            if (destDir.getCanonicalPath().startsWith(srcDir.getCanonicalPath() + File.separator)) {
                throw new IOException("Cannot move directory: " + srcDir + " to a subdirectory of itself: " + destDir);
            }
            copyDirectory(srcDir, destDir);
            deleteDirectory(srcDir);
            if (srcDir.exists()) {
                throw new IOException("Failed to delete original directory '" + srcDir +
                        "' after copy to '" + destDir + "'");
            }
        }
    }

    /**
     * Copied from Apache Commons, with the "File Must Not Exist" check removed
     * @param srcFile - The source file
     * @param destFile - The destination file
     * @param copyOptions - {@link StandardCopyOption} to be used with the move process
     * @throws IOException - Thrown if an IO error occurs
     */
    public static void moveFileInternal(final File srcFile, final File destFile, final CopyOption... copyOptions)
            throws IOException {
        validateMoveParameters(srcFile, destFile);
        requireFile(srcFile, "srcFile");
        final boolean rename = srcFile.renameTo(destFile);
        if (!rename) {
            copyFile(srcFile, destFile, copyOptions);
            if (!srcFile.delete()) {
                FileUtils.deleteQuietly(destFile);
                throw new IOException("Failed to delete original file '" + srcFile +
                        "' after copy to '" + destFile + "'");
            }
        }
    }

    /**
     * Check that input values are not null and that the source file/directory exists
     * @param source - The source file/directory
     * @param destination - The destination file/directory
     * @throws FileNotFoundException - Thrown if the source file/directory does not exist
     */
    private static void validateMoveParameters(final File source, final File destination) throws FileNotFoundException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        if (!source.exists()) {
            throw new FileNotFoundException("Source '" + source + "' does not exist");
        }
    }

    /**
     * Check if the source input is a directory
     * @param directory - The source directory
     * @param name - Identifier for the error message
     * @return - Return the directory if it's valid
     */
    private static File requireDirectory(final File directory, final String name) {
        Objects.requireNonNull(directory, name);
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("Parameter '" + name + "' is not a directory: '" + directory + "'");
        }
        return directory;
    }

    /**
     * Check if the source input is not a directory
     * @param file - The source file
     * @param name - Identifier for the error message
     * @return - Return the file if it's valid
     */
    private static File requireFile(final File file, final String name) {
        Objects.requireNonNull(file, name);
        if (!file.isFile()) {
            throw new IllegalArgumentException("Parameter '" + name + "' is not a file: " + file);
        }
        return file;
    }

    /**
     * Get a list of embedded jar files from the input jar
     * @param dir - The directory the jar was extracted to
     * @return - List of embedded jars
     */
    @NotNull
    public static List<File> embeddedJars(File dir) {
        List<File> returnJars = new ArrayList<>();

        // Directories
        File metaInf = new File(dir, META_DIR);
        File jarsDir = new File(metaInf, JARS_DIR);
        File jarJarDir = new File(metaInf, JARJAR_DIR);

        if (jarsDir.exists()) {
            File[] list = jarsDir.listFiles();

            if (list != null) {
                for (File jar : list) {
                    if (FilenameUtils.getExtension(jar.getName()).equalsIgnoreCase("jar"))
                        returnJars.add(jar);
                }
            }
        }

        if (jarJarDir.exists()) {
            File[] list = jarJarDir.listFiles();

            if (list != null) {
                for (File jar : list) {
                    if (FilenameUtils.getExtension(jar.getName()).equalsIgnoreCase("jar"))
                        returnJars.add(jar);
                }
            }
        }

        return returnJars;
    }

    /**
     * Get all text files from the input jar
     * @param dir - The directory the jar was extracted to
     * @return - List of text files
     */
    @NotNull
    public static List<File> getTextFiles(@NotNull File dir) throws IOException {
        List<File> returnFiles = new ArrayList<>();
        File[] list = dir.listFiles();
        if (list == null)
            return returnFiles;

        for (File file : list) {
            if (file.isDirectory()) {
                returnFiles.addAll(getTextFiles(file));
            } else {
                if (!FilenameUtils.getExtension(file.getName()).equalsIgnoreCase("class")) {
                    if (!FileChecks.isBinary(file))
                        returnFiles.add(file);
                }
            }
        }

        return returnFiles;
    }

    /**
     * Get a list of mixin configurations from the input jar
     * @param dir - The directory the jar was extracted to
     * @param includeRefmaps - Should reference maps be included in the search
     * @return - List of mixin configs and optionally refmaps
     * @throws IOException - Thrown when an IO error occurs
     */
    @NotNull
    public static List<File> getMixins(@NotNull File dir, boolean includeRefmaps) throws IOException {
        List<File> files = getTextFiles(dir);
        List<File> returnMixins = new ArrayList<>();

        for (File file : files) {
            if (FilenameUtils.getExtension(file.getName()).equalsIgnoreCase("json")) {
                String text = FileUtils.readFileToString(file, Charset.defaultCharset());

                if (includeRefmaps) {
                    if (text.contains("\"mappings\":") || text.contains("\"data\":")) {
                        returnMixins.add(file);
                        continue;
                    }
                }

                if (text.contains("\"package\":")) {
                    returnMixins.add(file);
                }
            }
        }

        return returnMixins;
    }

    /**
     * Get a list of refmaps from input jar
     * @param dir - The directory the jar was extracted to
     * @return - A list of mixin refmaps
     * @throws IOException - Thrown when an IO error occurs
     */
    @NotNull
    public static List<File> getRefmaps(@NotNull File dir) throws IOException {
        List<File> files = getTextFiles(dir);
        List<File> refmaps = new ArrayList<>();

        for (File file : files) {
            if (FilenameUtils.getExtension(file.getName()).equals("json")) {
                String text = FileUtils.readFileToString(file, Charset.defaultCharset());
                if (text.contains("\"mappings\":") || text.contains("\"data\":"))
                    refmaps.add(file);
            }
        }

        return refmaps;
    }

    /**
     * Get a list of accesswideners from the input jar
     * @param dir - The directory the jar was extracted to
     * @return - A list of access wideners
     * @throws IOException - Thrown when an IO error occurs
     */
    @NotNull
    public static List<File> getAccessWideners(@NotNull File dir) throws IOException {
        List<File> files = getTextFiles(dir);
        List<File> wideners = new ArrayList<>();

        for (File file : files) {
            if (FilenameUtils.getExtension(file.getName()).equals("accesswidener")) {
                wideners.add(file);
                continue;
            }

            FileInputStream fis = new FileInputStream(file);
            Scanner scanner = new Scanner(fis);
            if (scanner.hasNext()) {
                if (scanner.nextLine().startsWith("accessWidener"))
                    wideners.add(file);
            }
            scanner.close();
            fis.close();
        }

        return wideners;
    }

    /**
     * Get a list of platform services from the input jar
     * @param dir - The directory the jar was extracted to
     * @param group - The group to search for
     * @return - A list of service files
     */
    @NotNull
    public static List<File> getPlatformServices(@NotNull File dir, @NotNull String group) {
        List<File> services = new ArrayList<>();

        File metaInf = new File(dir, META_DIR);
        File servicesLocation = new File(metaInf, SERVICES_DIR);

        if (servicesLocation.exists()) {
            File[] list = servicesLocation.listFiles();
            if (list != null) {
                for (File service : list) {
                    if (FilenameUtils.getBaseName(service.getName()).contains(group))
                        services.add(service);
                }
            }
        }

        return services;
    }

    /**
     * Get the first directory from a file name
     * @param fileName - The input file name
     * @return - The name of the first directory specified in the file name
     */
    @NotNull
    public static String getFirstDirectory(@NotNull String fileName) {
        int end = fileName.indexOf(File.separator);
        if (end != -1) {
            return fileName.substring(0, end);
        }
        end = fileName.indexOf("/");
        if (end != -1) {
            return fileName.substring(0, end);
        }
        return "";
    }

    /**
     * Create a directory if it doesn't exist, or delete and recreate it if it does
     * @param dir - The input directory
     * @return - The new directory
     * @throws IOException - Thrown when an IO exception occurs
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @NotNull
    public static File createOrReCreate(@NotNull File dir) throws IOException {
        if (dir.exists())
            FileUtils.deleteQuietly(dir);

        if (!dir.exists())
            dir.mkdirs();

        return dir;
    }

    /**
     * Create a file if it doesn't exist, or delete and recreate it if it does
     * @param dir - The input directory
     * @return - The new file
     * @throws IOException - Thrown when an IO error occurs
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @NotNull
    public static File createOrReCreateF(@NotNull File dir) throws IOException {
        if (dir.exists())
            FileUtils.deleteQuietly(dir);

        if (!dir.exists())
            dir.createNewFile();

        return dir;
    }

    public static File resolveFile(Project project, Object obj) {
        if (obj == null) {
            throw new NullPointerException("Null Path");
        }

        if (obj instanceof String) {
            Task t = project.getTasks().getByName((String) obj);
            if (t instanceof AbstractArchiveTask)
                return ((AbstractArchiveTask)t).getArchiveFile().get().getAsFile();
        }

        if (obj instanceof File) {
            return (File) obj;
        }

        if (obj instanceof AbstractArchiveTask) {
            return ((AbstractArchiveTask)obj).getArchiveFile().get().getAsFile();
        }
        return project.file(obj);
    }
}
