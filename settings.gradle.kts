pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases")
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("net.neoforged.moddev.repositories") version "2.0.+"
}

// Extra repos for NeoForge module (cannot declare project-level repos with ModDevGradle)
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://repo.codemc.io/repository/maven-releases/")
        maven("https://repo.codemc.io/repository/maven-snapshots/")
        maven("https://jitpack.io")
    }
}

rootProject.name = "modl-minecraft"

include("api")
include("core")
include("bridge-core")
include("platforms:spigot")
include("platforms:spigot-sv")
include("platforms:velocity")
include("platforms:bungee")
include("platforms:fabric")
include("platforms:neoforge")
include("distribution")
