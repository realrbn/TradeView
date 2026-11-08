pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.kikugie.dev/releases")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("dev.kikugie.loom-back-compat") version "0.4.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        version("1.20.1-fabric", "1.20.1").buildscript("build.fabric-legacy.gradle.kts")
        version("1.21.1-fabric", "1.21.1").buildscript("build.fabric-legacy.gradle.kts")
        version("1.21.1-neoforge", "1.21.1").buildscript("build.neoforge.gradle.kts")
        version("1.21.11-fabric", "1.21.11").buildscript("build.fabric-legacy.gradle.kts")
        version("1.21.11-neoforge", "1.21.11").buildscript("build.neoforge.gradle.kts")
        version("26.2-fabric", "26.2").buildscript("build.fabric.gradle.kts")
        version("26.2-neoforge", "26.2").buildscript("build.neoforge.gradle.kts")
        version("26.3-fabric", "26.3").buildscript("build.fabric.gradle.kts")
        version("26.3-neoforge", "26.3").buildscript("build.neoforge.gradle.kts")
        vcsVersion = "26.3-fabric"
    }
}

rootProject.name = "TradeView"
