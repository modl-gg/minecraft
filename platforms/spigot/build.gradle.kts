import java.io.File
import java.security.MessageDigest

repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.grim.ac/snapshots")
    maven("https://repo.polar.top/repository/polar/")
}

val vulcanApiJar = layout.projectDirectory.file("libs/VulcanAPI.jar")
val vulcanApiSha256 = "fecd55639488c55b5a997604161dfb59f6845852ebe73971881cf6f39b65ed97"

fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val bytesRead = input.read(buffer)
            if (bytesRead == -1) break
            digest.update(buffer, 0, bytesRead)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val verifyVulcanApiJar by tasks.registering {
    group = "verification"
    description = "Verifies the vendored Vulcan API jar checksum."

    inputs.file(vulcanApiJar)

    doLast {
        val jarFile = vulcanApiJar.asFile
        if (!jarFile.isFile) {
            throw GradleException("Missing vendored Vulcan API jar: ${jarFile.relativeTo(projectDir)}")
        }

        val actualSha256 = sha256Hex(jarFile)
        if (actualSha256 != vulcanApiSha256) {
            throw GradleException(
                "Vulcan API jar checksum mismatch for ${jarFile.relativeTo(projectDir)}. " +
                        "Expected $vulcanApiSha256 but found $actualSha256."
            )
        }
    }
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
    compileOnly("io.github.revxrsal:lamp.brigadier:${property("lamp.version")}")
    compileOnly("com.mojang:brigadier:1.2.9")

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

tasks.named("compileJava") {
    dependsOn(verifyVulcanApiJar)
}
