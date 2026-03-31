pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        mavenCentral()
        gradlePluginPortal()
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
