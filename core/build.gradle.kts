import java.security.MessageDigest
import java.util.Base64

dependencies {
    api(project(":api"))
    compileOnly("gg.modl:proto:${property("proto.version")}")
    compileOnly("com.google.guava:guava:${property("guava.version")}")
    compileOnly("gg.modl.minecraft.cirrus:cirrus-api:${property("cirrus.version")}")
    compileOnly("io.github.revxrsal:lamp.common:${property("lamp.version")}")
    compileOnly("org.yaml:snakeyaml:${property("snakeyaml.version")}")
    compileOnly("org.apache.httpcomponents.client5:httpclient5:${property("httpclient5.version")}")
    compileOnly("org.java-websocket:Java-WebSocket:${property("java.websocket.version")}")
    compileOnly("net.kyori:adventure-api:${property("adventure.version")}")
    compileOnly("net.kyori:adventure-text-minimessage:${property("adventure.version")}")
    compileOnly("net.kyori:adventure-text-serializer-legacy:${property("adventure.version")}")
    compileOnly("net.kyori:adventure-text-serializer-gson:${property("adventure.version")}")
    compileOnly("io.netty:netty-all:${property("netty.version")}")

    testImplementation("io.github.revxrsal:lamp.common:${property("lamp.version")}")
    testImplementation("gg.modl:proto:${property("proto.version")}")
    testImplementation("org.java-websocket:Java-WebSocket:${property("java.websocket.version")}")
    testImplementation(platform("org.junit:junit-bom:${property("junit.bom.version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.google.code.gson:gson:${property("gson.version")}")
    testImplementation("org.yaml:snakeyaml:${property("snakeyaml.version")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// PluginInfo.java template filtering (replaces Maven templating-maven-plugin)
val pluginProps = mapOf(
    "plugin" to mapOf(
        "id" to project.findProperty("plugin.id"),
        "name" to project.findProperty("plugin.name"),
        "version" to project.version,
        "author" to project.findProperty("plugin.author"),
        "description" to project.findProperty("plugin.description"),
        "url" to project.findProperty("plugin.url"),
    )
)

val generateTemplates = tasks.register<Copy>("generateTemplates") {
    from("src/main/java-templates")
    into(layout.buildDirectory.dir("generated/sources/templates/java/main"))
    expand(pluginProps)
    filteringCharset = "UTF-8"
}

sourceSets.main {
    java.srcDir(generateTemplates)
}

tasks.test {
    useJUnitPlatform()
}

data class RuntimeLibrary(
    val constantName: String,
    val group: String,
    val artifact: String,
    val versionProperty: String,
)

val runtimeLibraries = listOf(
    RuntimeLibrary("SNAKEYAML", "org.yaml", "snakeyaml", "snakeyaml.version"),
    RuntimeLibrary("GSON", "com.google.code.gson", "gson", "gson.version"),
    RuntimeLibrary("HTTPCLIENT5", "org.apache.httpcomponents.client5", "httpclient5", "httpclient5.version"),
    RuntimeLibrary("HTTPCORE5", "org.apache.httpcomponents.core5", "httpcore5", "httpcore5.version"),
    RuntimeLibrary("HTTPCORE5_H2", "org.apache.httpcomponents.core5", "httpcore5-h2", "httpcore5.version"),
    RuntimeLibrary("JAVA_WEBSOCKET", "org.java-websocket", "Java-WebSocket", "java.websocket.version"),
    RuntimeLibrary("PACKETEVENTS_API", "gg.modl.minecraft.packetevents", "packetevents-api", "packetevents.version"),
    RuntimeLibrary("PACKETEVENTS_NETTY", "gg.modl.minecraft.packetevents", "packetevents-netty-common", "packetevents.version"),
    RuntimeLibrary("PACKETEVENTS_SPIGOT", "gg.modl.minecraft.packetevents", "packetevents-spigot", "packetevents.version"),
    RuntimeLibrary("PACKETEVENTS_BUNGEE", "gg.modl.minecraft.packetevents", "packetevents-bungeecord", "packetevents.version"),
    RuntimeLibrary("PACKETEVENTS_VELOCITY", "gg.modl.minecraft.packetevents", "packetevents-velocity", "packetevents.version"),
    RuntimeLibrary("PACKETEVENTS_FABRIC_COMMON", "gg.modl.minecraft.packetevents", "packetevents-fabric-common", "packetevents.version"),
    RuntimeLibrary("PACKETEVENTS_FABRIC_INTERMEDIARY", "gg.modl.minecraft.packetevents", "packetevents-fabric-intermediary", "packetevents.version"),
    RuntimeLibrary("PACKETEVENTS_FABRIC_OFFICIAL", "gg.modl.minecraft.packetevents", "packetevents-fabric-official", "packetevents.version"),
    RuntimeLibrary("ADVENTURE_NBT", "net.kyori", "adventure-nbt", "adventure.version"),
    RuntimeLibrary("LAMP_COMMON", "io.github.revxrsal", "lamp.common", "lamp.version"),
    RuntimeLibrary("LAMP_BRIGADIER", "io.github.revxrsal", "lamp.brigadier", "lamp.version"),
    RuntimeLibrary("LAMP_BUKKIT", "io.github.revxrsal", "lamp.bukkit", "lamp.version"),
    RuntimeLibrary("LAMP_VELOCITY", "io.github.revxrsal", "lamp.velocity", "lamp.version"),
    RuntimeLibrary("LAMP_BUNGEE", "io.github.revxrsal", "lamp.bungee", "lamp.version"),
    RuntimeLibrary("LAMP_FABRIC", "io.github.revxrsal", "lamp.fabric", "lamp.version"),
    RuntimeLibrary("SLF4J_API", "org.slf4j", "slf4j-api", "slf4j.version"),
    RuntimeLibrary("SLF4J_SIMPLE", "org.slf4j", "slf4j-simple", "slf4j.version"),
    RuntimeLibrary("CIRRUS_SPIGOT", "gg.modl.minecraft.cirrus", "cirrus-spigot", "cirrus.version"),
    RuntimeLibrary("CIRRUS_VELOCITY", "gg.modl.minecraft.cirrus", "cirrus-velocity", "cirrus.version"),
    RuntimeLibrary("CIRRUS_BUNGEECORD", "gg.modl.minecraft.cirrus", "cirrus-bungeecord", "cirrus.version"),
    RuntimeLibrary("CIRRUS_FABRIC", "gg.modl.minecraft.cirrus", "cirrus-fabric", "cirrus.version"),
    RuntimeLibrary("ADVENTURE_KEY", "net.kyori", "adventure-key", "adventure.version"),
    RuntimeLibrary("EXAMINATION_API", "net.kyori", "examination-api", "examination.version"),
    RuntimeLibrary("EXAMINATION_STRING", "net.kyori", "examination-string", "examination.version"),
    RuntimeLibrary("ADVENTURE_API", "net.kyori", "adventure-api", "adventure.version"),
    RuntimeLibrary("ADVENTURE_TEXT_SERIALIZER_LEGACY", "net.kyori", "adventure-text-serializer-legacy", "adventure.version"),
    RuntimeLibrary("ADVENTURE_TEXT_MINIMESSAGE", "net.kyori", "adventure-text-minimessage", "adventure.version"),
    RuntimeLibrary("ADVENTURE_TEXT_SERIALIZER_JSON", "net.kyori", "adventure-text-serializer-json", "adventure.version"),
    RuntimeLibrary("ADVENTURE_TEXT_SERIALIZER_GSON", "net.kyori", "adventure-text-serializer-gson", "adventure.version"),
    RuntimeLibrary("PROTOBUF_JAVA", "com.google.protobuf", "protobuf-java", "protobuf.java.version"),
    RuntimeLibrary("PROTOBUF_JAVA_UTIL", "com.google.protobuf", "protobuf-java-util", "protobuf.java.version"),
    RuntimeLibrary("GUAVA", "com.google.guava", "guava", "guava.version"),
    RuntimeLibrary("FAILUREACCESS", "com.google.guava", "failureaccess", "failureaccess.version"),
    RuntimeLibrary("PROTOVALIDATE", "build.buf", "protovalidate", "protovalidate.version"),
    RuntimeLibrary("CEL_CORE", "org.projectnessie.cel", "cel-core", "cel.version"),
    RuntimeLibrary("CEL_GENERATED_ANTLR", "org.projectnessie.cel", "cel-generated-antlr", "cel.version"),
    RuntimeLibrary("CEL_GENERATED_PB", "org.projectnessie.cel", "cel-generated-pb", "cel.version"),
    RuntimeLibrary("AGRONA", "org.agrona", "agrona", "agrona.version"),
    RuntimeLibrary("IPADDRESS", "com.github.seancfoley", "ipaddress", "ipaddress.version"),
    RuntimeLibrary("JAKARTA_MAIL_API", "jakarta.mail", "jakarta.mail-api", "jakarta.mail.version"),
    RuntimeLibrary("JAKARTA_ACTIVATION_API", "jakarta.activation", "jakarta.activation-api", "jakarta.activation.version"),
    RuntimeLibrary("MODL_PROTO", "gg.modl", "proto", "proto.version"),
)

val publishedRuntimeChecksums = mapOf(
    "gg.modl.minecraft.cirrus:cirrus-spigot:4.2.4" to "UBSO7Eenxuj/Xs2tvxEgOOOZnhA2BGM9cQqaFXJXxdI=",
    "gg.modl.minecraft.cirrus:cirrus-velocity:4.2.4" to "9wAG0uzxFitqPdb/UESODtJG8rezsF8QdTybrPxMoV8=",
    "gg.modl.minecraft.cirrus:cirrus-bungeecord:4.2.4" to "mi6v+Xa7F29OI4BOTVcuJSpz4Z+cI/m6BwmBKFmvg34=",
    "gg.modl.minecraft.cirrus:cirrus-fabric:4.2.4" to "LELadOgUGGv2iyKXM0u55N986FNLjmL6FwHqF/zegFs=",
)

val generateLibraryVersions = tasks.register("generateLibraryVersions") {
    val outputDir = layout.buildDirectory.dir("generated/sources/libraryVersions/java/main")
    val outputFile = outputDir.map { it.file("gg/modl/minecraft/core/LibraryVersions.java") }
    inputs.properties(runtimeLibraries.associate { library ->
        "${library.constantName}.version" to project.property(library.versionProperty).toString()
    })
    inputs.properties(runtimeLibraries.associate { library ->
        "${library.constantName}.coordinates" to "${library.group}:${library.artifact}"
    })
    outputs.dir(outputDir)

    doLast {
        fun resolveArtifact(library: RuntimeLibrary): java.io.File {
            val version = project.property(library.versionProperty).toString()
            val dependency = dependencies.create("${library.group}:${library.artifact}:$version")
            return configurations.detachedConfiguration(dependency).apply {
                isTransitive = false
                resolutionStrategy.useGlobalDependencySubstitutionRules = false
            }.singleFile
        }

        fun sha256Base64(file: java.io.File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) {
                        break
                    }
                    digest.update(buffer, 0, read)
                }
            }
            return Base64.getEncoder().encodeToString(digest.digest())
        }

        val constants = runtimeLibraries.joinToString("\n") { library ->
            val version = project.property(library.versionProperty).toString()
            val coordinate = "${library.group}:${library.artifact}:$version"
            val checksum = publishedRuntimeChecksums[coordinate] ?: sha256Base64(resolveArtifact(library))
            listOf(
                "    static final String ${library.constantName} = \"$version\";",
                "    static final String ${library.constantName}_CHECKSUM = \"$checksum\";"
            ).joinToString("\n")
        }
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                buildString {
                    appendLine("package gg.modl.minecraft.core;")
                    appendLine()
                    appendLine("// Generated by :core:generateLibraryVersions from Gradle-resolved runtime artifacts.")
                    appendLine("final class LibraryVersions {")
                    appendLine()
                    appendLine("    private LibraryVersions() {}")
                    appendLine()
                    appendLine(constants)
                    appendLine("}")
                }
            )
        }
    }
}

sourceSets.main {
    java.srcDir(generateLibraryVersions.map { layout.buildDirectory.dir("generated/sources/libraryVersions/java/main") })
}
