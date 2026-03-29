repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    compileOnly("net.md-5:bungeecord-api:${property("bungeecord.version")}")

    implementation(project(":core"))
    implementation(project(":api"))

    compileOnly("co.aikar:acf-bungee:${property("acf.version")}")
    compileOnly("gg.modl.minecraft.cirrus:cirrus-api:${property("cirrus.version")}")
    compileOnly("gg.modl.minecraft.cirrus:cirrus-bungeecord:${property("cirrus.version")}")

    implementation("net.byteflux:libby-core:${property("libby.version")}")
    implementation("net.byteflux:libby-bungee:${property("libby.version")}")

    compileOnly("com.github.retrooper:packetevents-api:${property("packetevents.version")}")
    compileOnly("com.github.retrooper:packetevents-bungeecord:${property("packetevents.version")}")

    compileOnly("net.kyori:adventure-api:${property("adventure.version")}")
    compileOnly("net.kyori:adventure-text-serializer-gson:${property("adventure.version")}")
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
