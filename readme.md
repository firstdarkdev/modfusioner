## ModFusioner

ModFusioner is a gradle plugin that allow you to merge multiple mod jars into a single jar. It's intended to be used by multi-loader projects created with Architectury, MultiLoader Template or unimined.

This plugin is based on, and contains code from [Forgix](https://github.com/PacifistMC/Forgix), but with massive optimizations, additional features and bug fixes.

***

### Usage

To use this plugin inside your project, first you have to add our maven.

To does this, open up settings.gradle and add the following:

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            url "https://maven.firstdarkdev.xyz/releases"
        }
    }
}
```

Next, in your ROOT `build.gradle` add:

![badge](https://maven.firstdarkdev.xyz/api/badge/latest/releases/com/hypherionmc/modutils/modfusioner?color=40c14a&name=modfusioner)

```groovy
plugins {
    id "com.hypherionmc.modutils.modfusioner" version "1.0.+"
}
```

Finally, add the following to your ROOT `build.gradle` file:

```groovy
fusioner {
    packageGroup = project.group // Package group of the merged jar. For example com.mymod.awesome
    mergedJarName = "MyModMerged-combo-1.20.2" // The name of the output jar
    outputDirectory = "artifacts/fused" // Where the merged jar will be stored. Defaults to artifacts/fused
    jarVersion = final_version // The version of the mod/jar

    // Forge Project
    forge {
        projectName = "Forge" // The name of the project that contains the forge code
        inputTaskName = "jar" // The name of the build task for the forge project
        // OR
        // inputFile = "Forge/build/libs/MyMod-1.0.jar" // Use a custom jar input

        // Mixin configuration files
        // Only required if the plugin doesn't detect it directly
        mixin "${mod_id}.mixins.json"
        mixin "${mod_id}-forge.mixins.json"

        // Packages that need to be relocated to the specific platform
        addRelocate "me.hypherionmc.mcdiscordformatter", "forge.me.hypherionmc.mcdiscordformatter"
    }

    // Fabric Project
    fabric {
        projectName = "Fabric" // The name of the project that contains the fabric code
        inputTaskName = "remapJar" // The name of the build task for the forge project
        // OR
        // inputFile = "Fabric/build/libs/MyMod-Fabric-1.0.jar" // Use a custom jar input

        // Packages that need to be relocated to the specific platform
        addRelocate "me.hypherionmc.mcdiscordformatter", "fabric.me.hypherionmc.mcdiscordformatter"
    }

    // Quilt Project
    quilt {
        projectName = "Quilt" // The name of the project that contains the quilt code
        inputTaskName = "remapJar" // The name of the build task for the forge project
        // OR
        // inputFile = "Quilt/build/libs/MyMod-Quilt-1.0.jar" // Use a custom jar input

        // Packages that need to be relocated to the specific platform
        addRelocate "me.hypherionmc.mcdiscordformatter", "fabric.me.hypherionmc.mcdiscordformatter"
    }

    // For "custom", the "projectName" is a required value.
    custom {
        projectName = "sponge" // This is the name of the project. This is a required field.
        inputTaskName = "jar" // The name of the build task for the forge project
        // OR
        // inputFile = "sponge/build/libs/MyMod-Sponge-1.0.jar" // Use a custom jar input

        additionalRelocate "org.my.lib" "sponge.org.my.lib" // This is an important one to know. This is how you can remap additional packages such as libraries and stuff.
        additionalRelocate "org.my.lib.another" "sponge.org.my.lib.another"
    }

    // Remove duplicate libraries or packages. Useful if you have shaded libraries in your mod
    relocateDuplicate "com.hypherionmc.sdlink.shaded"
}
```

Most values are optional, so you can only configure what you need. You need at least 2 projects in order for this plugin to work.

For additional help, please visit our [Discord Server](https://discord.firstdark.dev)

***

#### Credits & Licenses

Thanks to the Forgix authors for making the original plugin. It's an amazing plugin!

Plugin code is licensed under LGPL-2.1, which is the same as the original code it's based on