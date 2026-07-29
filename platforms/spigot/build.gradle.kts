import java.io.File
import java.security.MessageDigest

repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://libraries.minecraft.net/")
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
    compileOnly(libs.spigot.api)

    // Anticheat APIs
    compileOnly(libs.grim.api)
    compileOnly(libs.polar.api)
    compileOnly(files("libs/VulcanAPI.jar"))

    // Netty (provided by Minecraft server)
    compileOnly(libs.netty.all)

    // Adventure (loaded via Libby at runtime)
    compileOnly(libs.adventure.minimessage)
    compileOnly(libs.adventure.serializer.legacy)
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.serializer.gson)

    // Internal modules
    implementation(project(":core"))
    implementation(project(":api"))
    implementation(project(":bridge-core"))

    // Command framework
    compileOnly(libs.lamp.common)
    compileOnly(libs.lamp.bukkit)
    compileOnly(libs.lamp.brigadier)
    compileOnly(libs.brigadier)

    // Cirrus menu system
    compileOnly(libs.cirrus.api)
    compileOnly(libs.cirrus.spigot)

    // Libby runtime library loading
    implementation(libs.libby.core)
    implementation(libs.libby.bukkit)

    // PacketEvents
    compileOnly(libs.packetevents.api)
    compileOnly(libs.packetevents.spigot)

    // Replay recording
    implementation(libs.replay.format)
    implementation(libs.replay.recording)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.lamp.common)
    testImplementation(libs.lamp.bukkit)
    testImplementation(libs.cirrus.api)
    testImplementation(libs.cirrus.spigot)
    testImplementation(libs.spigot.api)
    testImplementation(libs.packetevents.spigot)
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
