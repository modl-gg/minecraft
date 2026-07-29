repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://repo.glaremasters.me/repository/public/") // bungeecord needs some weird ass stupid dependency couldnt find it anywhere else lol
}

dependencies {
    compileOnly(libs.bungeecord.api)

    implementation(project(":core"))
    implementation(project(":api"))

    compileOnly(libs.lamp.common)
    compileOnly(libs.lamp.bungee)
    compileOnly(libs.cirrus.api)
    compileOnly(libs.cirrus.bungeecord)

    implementation(libs.libby.core)
    implementation(libs.libby.bungee)

    compileOnly(libs.packetevents.api)
    compileOnly(libs.packetevents.bungeecord)

    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.serializer.gson)
}

tasks.processResources {
    filesMatching("bungee.yml") {
        expand(
            "plugin" to mapOf(
                "name" to project.findProperty("plugin.name"),
                "version" to project.version,
                "author" to project.findProperty("plugin.author"),
                "description" to project.findProperty("plugin.description"),
                "url" to project.findProperty("plugin.url"),
            ),
            "project" to mapOf(
                "groupId" to project.group,
            ),
        )
    }
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "gg.modl.minecraft.platform.bungee.plugin")
    }
}
