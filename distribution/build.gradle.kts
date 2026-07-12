plugins {
    id("com.gradleup.shadow")
}

import java.util.Properties
import java.util.zip.ZipFile

java {
    disableAutoTargetJvm()
}

val fabricLoomJar = project(":platforms:fabric").layout.buildDirectory.file("libs/fabric-${project.version}.jar")
val rootGradlew = rootProject.file("gradlew")
val packetEventsDir = rootProject.file("../minecraft-packetevents")
val packetEventsGradlew = packetEventsDir.resolve("gradlew")
val nestedPacketEventsFileName = "packetevents-modl.jar"

data class FabricModule(
    val key: String,
    val mcLabel: String,
) {
    val directory: String get() = "platforms/fabric-$key"
    val nestedJarFileName: String get() = "modl-fabric-$key.jar"
    val buildTaskName: String get() = "buildFabric$key"
}

val fabricModules = listOf(
    FabricModule("121", "1.21.1"),
    FabricModule("1214", "1.21.4"),
    FabricModule("1218", "1.21.8"),
    FabricModule("12111", "1.21.11"),
    FabricModule("26", "26.1.2"),
)

val nestedFabricImplementationFileNames = fabricModules.map { it.nestedJarFileName }

fun FabricModule.projectDir(): java.io.File = rootProject.file(directory)

fun FabricModule.remappedJar(): java.io.File =
    projectDir().resolve("build/libs/modl-fabric-$key-${project.version}.jar")

fun packetEventsVersion(): String {
    val properties = Properties()
    packetEventsDir.resolve("gradle.properties").inputStream().use(properties::load)
    val fullVersion = properties.getProperty("fullVersion")
    val snapshot = properties.getProperty("snapshot").toBoolean()
    return if (snapshot) "$fullVersion-SNAPSHOT" else fullVersion
}

val packetEventsFabricJar = provider {
    packetEventsDir.resolve("build/libs/packetevents-fabric-${packetEventsVersion()}.jar")
}

val generatedFabricMetadata = layout.buildDirectory.file("generated/fabricDistribution/fabric.mod.json")
val fabricMetadataSource = rootProject.file("platforms/fabric/src/main/resources/fabric.mod.json")

val fabricRuntimeSupport by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val fabricModuleBuildTasks = fabricModules.map { module ->
    tasks.register<Exec>(module.buildTaskName) {
        description = "Builds the Fabric ${module.mcLabel} module (separate Gradle build for its Loom version)"
        dependsOn(":core:jar", ":bridge-core:jar", ":api:jar")
        workingDir = rootProject.projectDir
        commandLine(
            rootGradlew.absolutePath,
            "-p", module.projectDir().absolutePath,
            "build", "-x", "test"
        )
        onlyIf { module.projectDir().resolve("build.gradle").exists() }
    }
}

val buildPacketEventsFabric by tasks.registering(Exec::class) {
    description = "Builds the forked PacketEvents Fabric jar for nesting in the Modl Fabric distribution"
    workingDir = packetEventsDir
    commandLine(packetEventsGradlew.absolutePath, ":fabric:jar")
    onlyIf { packetEventsGradlew.isFile }
}

val generateFabricDistributionMetadata by tasks.registering {
    description = "Generates Fabric metadata for the final distribution jar"
    inputs.file(fabricMetadataSource)
    inputs.property("version", project.version.toString())
    inputs.property("nestedFabricImplementationFileNames", nestedFabricImplementationFileNames.joinToString(","))
    inputs.property("nestedPacketEventsFileName", nestedPacketEventsFileName)
    outputs.file(generatedFabricMetadata)

    doLast {
        val source = fabricMetadataSource.readText()
        val expanded = source.replace("\${version}", project.version.toString())
        val newline = if (expanded.contains("\r\n")) "\r\n" else "\n"
        val licenseMarkerRegex = Regex("""  "license": "AGPL-3.0",(?:\r\n|\n)""")
        val licenseMarkerMatch = licenseMarkerRegex.find(expanded)
        val nestedJarFileNames = nestedFabricImplementationFileNames + nestedPacketEventsFileName
        val nestedJarsJson = nestedJarFileNames.joinToString(",$newline") { fileName ->
            "    { \"file\": \"META-INF/jars/$fileName\" }"
        }
        val withNestedJar = if (expanded.contains("\"jars\"")) {
            nestedJarFileNames.forEach { fileName ->
                check(expanded.contains("\"file\": \"META-INF/jars/$fileName\"")) {
                    "Existing Fabric metadata must register nested jar $fileName"
                }
            }
            expanded
        } else {
            check(licenseMarkerMatch != null) {
                "Unable to add nested jar metadata to ${fabricMetadataSource.absolutePath}"
            }
            expanded.replaceRange(
                licenseMarkerMatch.range,
                licenseMarkerMatch.value +
                    "  \"jars\": [$newline" +
                    nestedJarsJson + newline +
                    "  ],$newline"
            )
        }

        val output = generatedFabricMetadata.get().asFile
        output.parentFile.mkdirs()
        output.writeText(withNestedJar)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":bridge-core"))
    implementation(project(":platforms:spigot"))
    implementation(project(":platforms:spigot-sv"))
    implementation(project(":platforms:velocity"))
    implementation(project(":platforms:bungee"))
    implementation(libs.adventure.api)
    implementation(libs.adventure.nbt)
    implementation(libs.adventure.minimessage)
    implementation(libs.adventure.serializer.legacy)
    implementation(libs.adventure.serializer.gson)

    fabricRuntimeSupport(project(":api"))
    fabricRuntimeSupport(project(":core"))
    fabricRuntimeSupport(project(":bridge-core"))
    fabricRuntimeSupport(libs.libby.core)
    fabricRuntimeSupport(libs.libby.fabric)
}

tasks.shadowJar {
    archiveBaseName.set("modl")
    archiveClassifier.set("")

    relocate("com.google.protobuf", "gg.modl.libs.protobuf")

    exclude("**/module-info.class")
    exclude("com/google/gson/**")

    // Dependencies loaded at runtime via Libby (Libraries.PROTO_DEPS)
    exclude("gg/modl/proto/**")
    exclude("com/google/protobuf/**")
    exclude("com/google/common/**")
    exclude("com/google/thirdparty/**")
    exclude("com/google/errorprone/**")
    exclude("com/google/j2objc/**")
    exclude("com/google/code/**")
    exclude("google/protobuf/**")
    exclude("org/projectnessie/**")
    exclude("org/agrona/**")
    exclude("com/buf/**")
    exclude("build/buf/**")
    exclude("inet/ipaddr/**")
    exclude("org/checkerframework/**")
    exclude("jakarta/**")
    exclude("javax/annotation/**")

    from(rootProject.file("LICENSE.txt"))
}

val fabricJar by tasks.registering(Jar::class) {
    dependsOn(":platforms:fabric:remapJar")
    dependsOn(fabricModuleBuildTasks)
    dependsOn(buildPacketEventsFabric)
    dependsOn(generateFabricDistributionMetadata)

    archiveBaseName.set("modl-fabric")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    exclude("**/module-info.class")

    from(rootProject.file("LICENSE.txt")) {
        rename { "LICENSE_modl.txt" }
    }

    from(zipTree(fabricLoomJar)) {
        exclude("META-INF/**")
        exclude("fabric.mod.json")
    }
    from(generatedFabricMetadata)

    from(provider { fabricRuntimeSupport.files.map { zipTree(it) } }) {
        exclude("META-INF/**")
        exclude("**/module-info.class")
    }

    fabricModules.forEach { module ->
        from(module.remappedJar()) {
            into("META-INF/jars")
            rename { module.nestedJarFileName }
        }
    }

    from(packetEventsFabricJar) {
        into("META-INF/jars")
        rename { nestedPacketEventsFileName }
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
    dependsOn(fabricJar)
}

val universalDistributionJar = layout.buildDirectory.file("libs/modl-${project.version}.jar")
val fabricDistributionJar = layout.buildDirectory.file("libs/modl-fabric-${project.version}.jar")

fun zipEntryNames(file: java.io.File): Set<String> =
    ZipFile(file).use { zip -> zip.entries().asSequence().map { it.name }.toSet() }

tasks.register("verifySplitDistributionArtifacts") {
    description = "Verifies that distribution produces separate universal and Fabric jars"
    group = "verification"
    dependsOn(tasks.shadowJar)
    dependsOn(fabricJar)
    inputs.file(universalDistributionJar)
    inputs.file(fabricDistributionJar)

    doLast {
        val universalFile = universalDistributionJar.get().asFile
        check(universalFile.isFile) { "Missing universal distribution jar: ${universalFile.absolutePath}" }
        val universalEntries = zipEntryNames(universalFile)
        check("fabric.mod.json" !in universalEntries) { "Universal jar must not contain fabric.mod.json" }
        check("modl.accesswidener" !in universalEntries) { "Universal jar must not contain modl.accesswidener" }
        check(universalEntries.none { it.startsWith("gg/modl/minecraft/fabric/") }) {
            "Universal jar must not contain Fabric platform classes"
        }

        val fabricFile = fabricDistributionJar.get().asFile
        check(fabricFile.isFile) { "Missing Fabric distribution jar: ${fabricFile.absolutePath}" }
        val fabricEntries = zipEntryNames(fabricFile)
        check("fabric.mod.json" in fabricEntries) { "Fabric jar must contain fabric.mod.json" }
        check("modl.accesswidener" in fabricEntries) { "Fabric jar must contain modl.accesswidener" }
        val fabricMetadata = ZipFile(fabricFile).use { zip ->
            zip.getInputStream(zip.getEntry("fabric.mod.json")).bufferedReader().readText()
        }
        val nestedJarFileNames = nestedFabricImplementationFileNames + nestedPacketEventsFileName
        nestedJarFileNames.forEach { fileName ->
            check(fabricMetadata.contains("\"file\": \"META-INF/jars/$fileName\"")) {
                "Fabric metadata must register nested jar $fileName"
            }
        }
        check("gg/modl/minecraft/fabric/ModlFabricMod.class" in fabricEntries) {
            "Fabric jar must contain the Fabric shell entrypoint"
        }
        check("gg/modl/minecraft/api/LibraryRecord.class" in fabricEntries) {
            "Fabric jar must contain shared API classes"
        }
        check("gg/modl/minecraft/core/PluginLoader.class" in fabricEntries) {
            "Fabric jar must contain shared core classes"
        }
        check("gg/modl/minecraft/bridge/AbstractBridgeComponent.class" in fabricEntries) {
            "Fabric jar must contain shared bridge classes"
        }
        check("com/alessiodp/libby/FabricLibraryManager.class" in fabricEntries) {
            "Fabric jar must contain Libby Fabric runtime loader"
        }
        nestedJarFileNames.forEach { fileName ->
            check("META-INF/jars/$fileName" in fabricEntries) {
                "Fabric jar must contain nested jar $fileName"
            }
        }
        val implementationPrefixes = listOf(
            "gg/modl/minecraft/fabric/v1_21_1/",
            "gg/modl/minecraft/fabric/v1_21_4/",
            "gg/modl/minecraft/fabric/v1_21_8/",
            "gg/modl/minecraft/fabric/v1_21_11/",
            "gg/modl/minecraft/fabric/v26/"
        )
        implementationPrefixes.forEach { prefix ->
            check(fabricEntries.none { it.startsWith(prefix) }) {
                "Fabric implementation classes must remain inside nested jars, but found root entries under $prefix"
            }
        }
    }
}
