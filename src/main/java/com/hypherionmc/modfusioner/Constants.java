/*
 * This file is part of ModFusioner, licensed under the GNU Lesser General Public License v2.1.
 *
 * This project is based on, and contains code from https://github.com/PacifistMC/Forgix, licensed under the same license.
 * See their license here: https://github.com/PacifistMC/Forgix/blob/main/LICENSE
 *
 * Copyright HypherionSA and Contributors
 * Forgix Code Copyright by their contributors and Ran-Mewo
 */
package com.hypherionmc.modfusioner;

import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;

/**
 * @author HypherionSA
 * Class that contain constant values used througout the plugin
 */
public class Constants {

    public static final String TASK_GROUP = "modfusioner";
    public static final String TASK_NAME = "fusejars";
    public static final String EXTENSION_NAME = "fusioner";
    public static final String MANIFEST_KEY = "ModFusioner-Version";

    public static Set<PosixFilePermission> filePerms = new HashSet<>();

    static {
        filePerms.add(PosixFilePermission.OTHERS_EXECUTE);
        filePerms.add(PosixFilePermission.OTHERS_WRITE);
        filePerms.add(PosixFilePermission.OTHERS_READ);
        filePerms.add(PosixFilePermission.OWNER_EXECUTE);
        filePerms.add(PosixFilePermission.OWNER_WRITE);
        filePerms.add(PosixFilePermission.OWNER_READ);
        filePerms.add(PosixFilePermission.GROUP_EXECUTE);
        filePerms.add(PosixFilePermission.GROUP_WRITE);
        filePerms.add(PosixFilePermission.GROUP_READ);
    }

}
