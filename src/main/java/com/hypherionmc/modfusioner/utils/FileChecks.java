package com.hypherionmc.modfusioner.utils;

import org.apache.tika.Tika;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * @author HypherionSA
 * Utility class to perform some checks on files
 */
public class FileChecks {

    /**
     * Try to determine if a file is a binary or text file
     * @param file - The file to test
     * @return - True if binary
     */
    public static boolean isBinary(@NotNull File file) throws IOException {
        Tika tika = new Tika();
        String detectedType = tika.detect(file);
        return detectedType.equals("application/octet-stream");
    }

    /**
     * Test to see if input file is a ZIP file
     * @param file - The file to test
     * @return - True if zip
     */
    public static boolean isZipFile(@NotNull File file) {
        try {
            if (file.isDirectory()) return false;
            byte[] bytes = new byte[4];
            FileInputStream fis = new FileInputStream(file);
            if (fis.read(bytes) != 4) {
                return false;
            }
            fis.close();
            final int header = bytes[0] + (bytes[1] << 8) + (bytes[2] << 16) + (bytes[3] << 24);
            return 0x04034b50 == header;
        } catch (IOException e) {
            return false;
        }
    }
}
