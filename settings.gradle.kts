pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "modl-minecraft"

// Shared protobuf types
val protoDir = file("../proto")
if (protoDir.exists()) {
    includeBuild("../proto")
}

include("api")
include("core")
include("bridge-core")
include("platforms:spigot")
include("platforms:spigot-sv")
include("platforms:velocity")
include("platforms:bungee")
include("platforms:fabric")
include("distribution")
