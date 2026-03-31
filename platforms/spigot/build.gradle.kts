repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.grim.ac/snapshots")
    maven("https://repo.polar.top/repository/polar/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:${property("spigot.version")}")

    // Anticheat APIs
    compileOnly("ac.grim.grimac:GrimAPI:1.0.0")
    compileOnly("top.polar:api:2.3.0")
    compileOnly(files("libs/VulcanAPI.jar"))

    // Netty (provided by Minecraft server)
    compileOnly("io.netty:netty-all:4.1.97.Final")

    // Adventure (loaded via Libby at runtime)
    compileOnly("net.kyori:adventure-text-minimessage:${property("adventure.version")}")
    compileOnly("net.kyori:adventure-text-serializer-legacy:${property("adventure.version")}")
    compileOnly("net.kyori:adventure-api:${property("adventure.version")}")
    compileOnly("net.kyori:adventure-text-serializer-gson:${property("adventure.version")}")

    // Internal modules
    implementation(project(":core"))
    implementation(project(":api"))
    implementation(project(":bridge-core"))

    // Command framework
    compileOnly("co.aikar:acf-bukkit:${property("acf.version")}")

    // Cirrus menu system
    compileOnly("gg.modl.minecraft.cirrus:cirrus-api:${property("cirrus.version")}")
    compileOnly("gg.modl.minecraft.cirrus:cirrus-spigot:${property("cirrus.version")}")

    // Libby runtime library loading
    implementation("com.alessiodp.libby:libby-core:${property("libby.version")}")
    implementation("com.alessiodp.libby:libby-bukkit:${property("libby.version")}")

    // PacketEvents
    compileOnly("com.github.retrooper:packetevents-api:${property("packetevents.version")}")
    compileOnly("com.github.retrooper:packetevents-spigot:${property("packetevents.version")}")

    // Replay recording
    implementation("gg.modl.replay:replay-format:1.0.0-SNAPSHOT")
    implementation("gg.modl.replay:modl-replay-recording:1.0.0-SNAPSHOT")
}

tasks.processResources {
    filesMatching("plugin.yml") {
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
        attributes("Automatic-Module-Name" to "gg.modl.minecraft.platform.spigot.plugin")
    }
}
