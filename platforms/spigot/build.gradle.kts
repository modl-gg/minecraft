repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.grim.ac/snapshots")
    maven("https://repo.polar.top/repository/polar/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:${property("spigot.version")}")

    // Anticheat APIs
    compileOnly("ac.grim.grimac:GrimAPI:${property("grim.api.version")}")
    compileOnly("top.polar:api:${property("polar.api.version")}")
    compileOnly(files("libs/VulcanAPI.jar"))

    // Netty (provided by Minecraft server)
    compileOnly("io.netty:netty-all:${property("netty.version")}")

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
    compileOnly("io.github.revxrsal:lamp.common:${property("lamp.version")}")
    compileOnly("io.github.revxrsal:lamp.bukkit:${property("lamp.version")}")

    // Cirrus menu system
    compileOnly("gg.modl.minecraft.cirrus:cirrus-api:${property("cirrus.version")}")
    compileOnly("gg.modl.minecraft.cirrus:cirrus-spigot:${property("cirrus.version")}")

    // Libby runtime library loading
    implementation("com.alessiodp.libby:libby-core:${property("libby.version")}")
    implementation("com.alessiodp.libby:libby-bukkit:${property("libby.version")}")

    // PacketEvents
    compileOnly("gg.modl.minecraft.packetevents:packetevents-api:${property("packetevents.version")}")
    compileOnly("gg.modl.minecraft.packetevents:packetevents-spigot:${property("packetevents.version")}")

    // Replay recording
    implementation("gg.modl.minecraft.replay:replay-format:${property("replay.format.version")}")
    implementation("gg.modl.minecraft.replay:modl-replay-recording:${property("replay.recording.version")}")

    testImplementation(platform("org.junit:junit-bom:${property("junit.bom.version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-inline:${property("mockito.inline.version")}")
    testImplementation("org.mockito:mockito-junit-jupiter:${property("mockito.junit.jupiter.version")}")
    testImplementation("io.github.revxrsal:lamp.common:${property("lamp.version")}")
    testImplementation("io.github.revxrsal:lamp.bukkit:${property("lamp.version")}")
    testImplementation("gg.modl.minecraft.cirrus:cirrus-api:${property("cirrus.version")}")
    testImplementation("gg.modl.minecraft.cirrus:cirrus-spigot:${property("cirrus.version")}")
    testImplementation("org.spigotmc:spigot-api:${property("spigot.version")}")
    testImplementation("gg.modl.minecraft.packetevents:packetevents-spigot:${property("packetevents.version")}")
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

tasks.test {
    useJUnitPlatform()
}
