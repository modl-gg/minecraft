pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "modl-minecraft"

val localCirrusBuild = file("../minecraft-cirrus")
if (localCirrusBuild.isDirectory) {
    includeBuild(localCirrusBuild)
}

val localReplayRecordingBuild = file("../minecraft-replay-recording")
if (localReplayRecordingBuild.isDirectory) {
    includeBuild(localReplayRecordingBuild)
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
