pluginManagement {
    val rootGradleProperties = java.util.Properties().apply {
        file("gradle.properties").reader().use { reader ->
            load(reader)
        }
    }
    val shadowPluginVersion = rootGradleProperties.getProperty("shadow.version")
    val fabricLoomModernVersion = rootGradleProperties.getProperty("fabric.loom.version.modern")

    repositories {
        maven("https://maven.fabricmc.net/")
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.gradleup.shadow") version (shadowPluginVersion ?: throw GradleException("shadow.version missing from gradle.properties"))
        id("fabric-loom") version (fabricLoomModernVersion ?: throw GradleException("fabric.loom.version.modern missing from gradle.properties"))
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
include("distribution")
