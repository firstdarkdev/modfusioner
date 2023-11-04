/*
 * This file is part of ModFusioner, licensed under the GNU Lesser General Public License v2.1.
 *
 * This project is based on, and contains code from https://github.com/PacifistMC/Forgix, licensed under the same license.
 * See their license here: https://github.com/PacifistMC/Forgix/blob/main/LICENSE
 *
 * Copyright HypherionSA and Contributors
 * Forgix Code Copyright by their contributors and Ran-Mewo
 */
package com.hypherionmc.modfusioner.utils;

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
    public static boolean isBinary(@NotNull File file) {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            int size = (int) Math.min(file.length(), 4096);
            byte[] data = new byte[size];
            int bytesRead = inputStream.read(data, 0, size);

            for (int i = 0; i < bytesRead; i++) {
                if (data[i] == 0) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
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
