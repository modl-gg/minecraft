import java.security.MessageDigest
import java.util.Base64
import org.gradle.api.artifacts.VersionCatalogsExtension

dependencies {
    api(project(":api"))
    compileOnly(libs.libby.core)
    compileOnly(libs.proto)
    compileOnly(libs.guava)
    compileOnly(libs.cirrus.api)
    compileOnly(libs.lamp.common)
    compileOnly(libs.snakeyaml)
    compileOnly(libs.httpclient5)
    compileOnly(libs.java.websocket)
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)
    compileOnly(libs.adventure.serializer.legacy)
    compileOnly(libs.adventure.serializer.gson)
    compileOnly(libs.netty.all)

    testImplementation(libs.lamp.common)
    testImplementation(libs.proto)
    // proto's gencode is stamped at protobuf.java.version; override the proto POM's transitive
    // protobuf-java so the test runtime is not older than the linked gencode version.
    testImplementation(libs.protobuf.java)
    testImplementation(libs.protobuf.util)
    testImplementation(libs.cirrus.api)
    testImplementation(libs.packetevents.api)
    testImplementation(libs.java.websocket)
    testImplementation(libs.httpclient5)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.serializer.legacy)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.gson)
    testImplementation(libs.snakeyaml)
    testRuntimeOnly(libs.junit.platform.launcher)
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

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun catalogVersion(alias: String): String =
    versionCatalog.findVersion(alias).orElseThrow {
        GradleException("Missing version alias '$alias' in libs.versions.toml")
    }.requiredVersion

data class RuntimeLibrary(
    val constantName: String,
    val group: String,
    val artifact: String,
    val versionAlias: String,
)

val runtimeLibraries = listOf(
    RuntimeLibrary("SNAKEYAML", "org.yaml", "snakeyaml", "snakeyaml"),
    RuntimeLibrary("GSON", "com.google.code.gson", "gson", "gson"),
    RuntimeLibrary("HTTPCLIENT5", "org.apache.httpcomponents.client5", "httpclient5", "httpclient5"),
    RuntimeLibrary("HTTPCORE5", "org.apache.httpcomponents.core5", "httpcore5", "httpcore5"),
    RuntimeLibrary("HTTPCORE5_H2", "org.apache.httpcomponents.core5", "httpcore5-h2", "httpcore5"),
    RuntimeLibrary("JAVA_WEBSOCKET", "org.java-websocket", "Java-WebSocket", "java-websocket"),
    RuntimeLibrary("PACKETEVENTS_API", "gg.modl.minecraft.packetevents", "packetevents-api", "packetevents"),
    RuntimeLibrary("PACKETEVENTS_NETTY", "gg.modl.minecraft.packetevents", "packetevents-netty-common", "packetevents"),
    RuntimeLibrary("PACKETEVENTS_SPIGOT", "gg.modl.minecraft.packetevents", "packetevents-spigot", "packetevents"),
    RuntimeLibrary("PACKETEVENTS_BUNGEE", "gg.modl.minecraft.packetevents", "packetevents-bungeecord", "packetevents"),
    RuntimeLibrary("PACKETEVENTS_VELOCITY", "gg.modl.minecraft.packetevents", "packetevents-velocity", "packetevents"),
    RuntimeLibrary("PACKETEVENTS_FABRIC_COMMON", "gg.modl.minecraft.packetevents", "packetevents-fabric-common", "packetevents"),
    RuntimeLibrary("PACKETEVENTS_FABRIC_INTERMEDIARY", "gg.modl.minecraft.packetevents", "packetevents-fabric-intermediary", "packetevents"),
    RuntimeLibrary("PACKETEVENTS_FABRIC_OFFICIAL", "gg.modl.minecraft.packetevents", "packetevents-fabric-official", "packetevents"),
    RuntimeLibrary("ADVENTURE_NBT", "net.kyori", "adventure-nbt", "adventure"),
    RuntimeLibrary("LAMP_COMMON", "io.github.revxrsal", "lamp.common", "lamp"),
    RuntimeLibrary("LAMP_BRIGADIER", "io.github.revxrsal", "lamp.brigadier", "lamp"),
    RuntimeLibrary("LAMP_BUKKIT", "io.github.revxrsal", "lamp.bukkit", "lamp"),
    RuntimeLibrary("LAMP_VELOCITY", "io.github.revxrsal", "lamp.velocity", "lamp"),
    RuntimeLibrary("LAMP_BUNGEE", "io.github.revxrsal", "lamp.bungee", "lamp"),
    RuntimeLibrary("LAMP_FABRIC", "io.github.revxrsal", "lamp.fabric", "lamp"),
    RuntimeLibrary("SLF4J_API", "org.slf4j", "slf4j-api", "slf4j"),
    RuntimeLibrary("SLF4J_SIMPLE", "org.slf4j", "slf4j-simple", "slf4j"),
    RuntimeLibrary("CIRRUS_SPIGOT", "gg.modl.minecraft.cirrus", "cirrus-spigot", "cirrus"),
    RuntimeLibrary("CIRRUS_VELOCITY", "gg.modl.minecraft.cirrus", "cirrus-velocity", "cirrus"),
    RuntimeLibrary("CIRRUS_BUNGEECORD", "gg.modl.minecraft.cirrus", "cirrus-bungeecord", "cirrus"),
    RuntimeLibrary("CIRRUS_FABRIC", "gg.modl.minecraft.cirrus", "cirrus-fabric", "cirrus"),
    RuntimeLibrary("ADVENTURE_KEY", "net.kyori", "adventure-key", "adventure"),
    RuntimeLibrary("EXAMINATION_API", "net.kyori", "examination-api", "examination"),
    RuntimeLibrary("EXAMINATION_STRING", "net.kyori", "examination-string", "examination"),
    RuntimeLibrary("ADVENTURE_API", "net.kyori", "adventure-api", "adventure"),
    RuntimeLibrary("ADVENTURE_TEXT_SERIALIZER_LEGACY", "net.kyori", "adventure-text-serializer-legacy", "adventure"),
    RuntimeLibrary("ADVENTURE_TEXT_MINIMESSAGE", "net.kyori", "adventure-text-minimessage", "adventure"),
    RuntimeLibrary("ADVENTURE_TEXT_SERIALIZER_JSON", "net.kyori", "adventure-text-serializer-json", "adventure"),
    RuntimeLibrary("ADVENTURE_TEXT_SERIALIZER_GSON", "net.kyori", "adventure-text-serializer-gson", "adventure"),
    RuntimeLibrary("PROTOBUF_JAVA", "com.google.protobuf", "protobuf-java", "protobuf-java"),
    RuntimeLibrary("PROTOBUF_JAVA_UTIL", "com.google.protobuf", "protobuf-java-util", "protobuf-java"),
    RuntimeLibrary("GUAVA", "com.google.guava", "guava", "guava"),
    RuntimeLibrary("FAILUREACCESS", "com.google.guava", "failureaccess", "failureaccess"),
    RuntimeLibrary("PROTOVALIDATE", "build.buf", "protovalidate", "protovalidate"),
    RuntimeLibrary("CEL_CORE", "org.projectnessie.cel", "cel-core", "cel"),
    RuntimeLibrary("CEL_GENERATED_ANTLR", "org.projectnessie.cel", "cel-generated-antlr", "cel"),
    RuntimeLibrary("CEL_GENERATED_PB", "org.projectnessie.cel", "cel-generated-pb", "cel"),
    RuntimeLibrary("AGRONA", "org.agrona", "agrona", "agrona"),
    RuntimeLibrary("IPADDRESS", "com.github.seancfoley", "ipaddress", "ipaddress"),
    RuntimeLibrary("JAKARTA_MAIL_API", "jakarta.mail", "jakarta.mail-api", "jakarta-mail"),
    RuntimeLibrary("JAKARTA_ACTIVATION_API", "jakarta.activation", "jakarta.activation-api", "jakarta-activation"),
    RuntimeLibrary("MODL_PROTO", "gg.modl", "proto", "proto"),
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
        "${library.constantName}.version" to catalogVersion(library.versionAlias)
    })
    inputs.properties(runtimeLibraries.associate { library ->
        "${library.constantName}.coordinates" to "${library.group}:${library.artifact}"
    })
    outputs.dir(outputDir)

    doLast {
        fun resolveArtifact(library: RuntimeLibrary): java.io.File {
            val version = catalogVersion(library.versionAlias)
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
            val version = catalogVersion(library.versionAlias)
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
