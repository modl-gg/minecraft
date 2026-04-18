plugins {
    id("com.gradleup.shadow") version "9.3.1"
}

java {
    disableAutoTargetJvm()
}

val fabricLoomJar = project(":platforms:fabric").layout.buildDirectory.file("libs/fabric-${project.version}.jar")
val rootGradlew = rootProject.file("gradlew")
val fabric12111Dir = rootProject.file("platforms/fabric-12111")
val fabric12111Jar = fabric12111Dir.resolve("build/libs/modl-fabric-12111-${project.version}.jar")
val fabric26Dir = rootProject.file("platforms/fabric-26")
val fabric26Jar = fabric26Dir.resolve("build/libs/modl-fabric-26-${project.version}.jar")
val fabric121Dir = rootProject.file("platforms/fabric-121")
val fabric121Jar = fabric121Dir.resolve("build/libs/modl-fabric-121-${project.version}.jar")
val fabric1214Dir = rootProject.file("platforms/fabric-1214")
val fabric1214Jar = fabric1214Dir.resolve("build/libs/modl-fabric-1214-${project.version}.jar")
val packeteventsFabricNested by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val packeteventsExtractedDir = layout.buildDirectory.dir("packetevents-fabric-unpacked")
val packeteventsInnerJarDir = layout.buildDirectory.dir("packetevents-fabric-inner")

val packeteventsStringReplacements = listOf(
    "com.github.retrooper.packetevents" to "gg.modl.libs.packetevents.api",
    "io.github.retrooper.packetevents" to "gg.modl.libs.packetevents.impl"
)

val buildFabric12111 by tasks.registering(Exec::class) {
    description = "Builds the Fabric 1.21.11 module (separate Gradle build for older Loom)"
    dependsOn(":core:jar", ":bridge-core:jar", ":api:jar")
    workingDir = rootProject.projectDir
    commandLine(
        rootGradlew.absolutePath,
        "-p", fabric12111Dir.absolutePath,
        "build", "-x", "test"
    )
    onlyIf { fabric12111Dir.resolve("build.gradle").exists() }
}

val buildFabric26 by tasks.registering(Exec::class) {
    description = "Builds the Fabric 26.1 module (separate Gradle build due to Loom version conflict)"
    dependsOn(":core:jar", ":bridge-core:jar", ":api:jar")
    workingDir = rootProject.projectDir
    commandLine(
        rootGradlew.absolutePath,
        "-p", fabric26Dir.absolutePath,
        "build", "-x", "test"
    )
    onlyIf { fabric26Dir.resolve("build.gradle").exists() }
}

val buildFabric121 by tasks.registering(Exec::class) {
    description = "Builds the Fabric 1.21.1 module (separate Gradle build for older Loom)"
    dependsOn(":core:jar", ":bridge-core:jar", ":api:jar")
    workingDir = rootProject.projectDir
    commandLine(
        rootGradlew.absolutePath,
        "-p", fabric121Dir.absolutePath,
        "build", "-x", "test"
    )
    onlyIf { fabric121Dir.resolve("build.gradle").exists() }
}

val buildFabric1214 by tasks.registering(Exec::class) {
    description = "Builds the Fabric 1.21.4 module (separate Gradle build for older Loom)"
    dependsOn(":core:jar", ":bridge-core:jar", ":api:jar")
    workingDir = rootProject.projectDir
    commandLine(
        rootGradlew.absolutePath,
        "-p", fabric1214Dir.absolutePath,
        "build", "-x", "test"
    )
    onlyIf { fabric1214Dir.resolve("build.gradle").exists() }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":bridge-core"))
    implementation(project(":platforms:spigot"))
    implementation(project(":platforms:spigot-sv"))
    implementation(project(":platforms:velocity"))
    implementation(project(":platforms:bungee"))
    // Libby Fabric adapter (needed by Fabric platform at runtime)
    implementation("com.alessiodp.libby:libby-fabric:${property("libby.version")}")
    add(packeteventsFabricNested.name, "com.github.retrooper:packetevents-fabric:2.12.0-SNAPSHOT")
}

val unpackPacketeventsFabric by tasks.registering(Sync::class) {
    from(provider {
        if (packeteventsFabricNested.files.isEmpty()) files() else zipTree(packeteventsFabricNested.singleFile)
    })
    into(packeteventsExtractedDir)
}

fun registerRelocatedPacketeventsJar(
    taskName: String,
    sourceFileName: String,
    rewrittenResources: List<String> = emptyList()
) = tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>(taskName) {
    dependsOn(unpackPacketeventsFabric)
    archiveFileName.set(sourceFileName)
    destinationDirectory.set(packeteventsInnerJarDir)
    from(provider {
        zipTree(packeteventsExtractedDir.get().file("META-INF/jars/$sourceFileName").asFile)
    })
    relocate("com.github.retrooper.packetevents", "gg.modl.libs.packetevents.api")
    relocate("io.github.retrooper.packetevents", "gg.modl.libs.packetevents.impl")
    rewrittenResources.forEach { resource ->
        filesMatching(resource) {
            filter { line: String ->
                packeteventsStringReplacements.fold(line) { acc, (fromValue, toValue) ->
                    acc.replace(fromValue, toValue)
                }
            }
        }
    }
}

val relocatedPacketeventsApiJar = registerRelocatedPacketeventsJar(
    "relocatedPacketeventsApiJar",
    "packetevents-api-2.12.0-SNAPSHOT.jar"
)

val relocatedPacketeventsNettyJar = registerRelocatedPacketeventsJar(
    "relocatedPacketeventsNettyJar",
    "packetevents-netty-common-2.12.0-SNAPSHOT.jar"
)

val relocatedPacketeventsFabricCommonJar = registerRelocatedPacketeventsJar(
    "relocatedPacketeventsFabricCommonJar",
    "packetevents-fabric-common-2.12.0-SNAPSHOT.jar",
    listOf("fabric.mod.json")
)

val relocatedPacketeventsFabricIntermediaryJar = registerRelocatedPacketeventsJar(
    "relocatedPacketeventsFabricIntermediaryJar",
    "packetevents-fabric-intermediary-2.12.0-SNAPSHOT.jar",
    listOf("fabric.mod.json", "packetevents.mixins.json")
)

val relocatedPacketeventsFabricOfficialJar = registerRelocatedPacketeventsJar(
    "relocatedPacketeventsFabricOfficialJar",
    "packetevents-fabric-official-2.12.0-SNAPSHOT.jar",
    listOf("fabric.mod.json", "packetevents.mixins.json")
)

val packeteventsFabricNestedJar by tasks.registering(Jar::class) {
    dependsOn(
        unpackPacketeventsFabric,
        relocatedPacketeventsApiJar,
        relocatedPacketeventsNettyJar,
        relocatedPacketeventsFabricCommonJar,
        relocatedPacketeventsFabricIntermediaryJar,
        relocatedPacketeventsFabricOfficialJar
    )
    archiveFileName.set("packetevents-fabric.jar")
    destinationDirectory.set(layout.buildDirectory.dir("packetevents-fabric-nested"))
    from(provider { zipTree(packeteventsFabricNested.singleFile) }) {
        exclude("META-INF/jars/*.jar")
    }
    into("META-INF/jars") {
        from(provider {
            packeteventsExtractedDir.get().dir("META-INF/jars").asFileTree.matching {
                exclude(
                    "packetevents-api-2.12.0-SNAPSHOT.jar",
                    "packetevents-netty-common-2.12.0-SNAPSHOT.jar",
                    "packetevents-fabric-common-2.12.0-SNAPSHOT.jar",
                    "packetevents-fabric-intermediary-2.12.0-SNAPSHOT.jar",
                    "packetevents-fabric-official-2.12.0-SNAPSHOT.jar"
                )
            }
        })
        from(relocatedPacketeventsApiJar)
        from(relocatedPacketeventsNettyJar)
        from(relocatedPacketeventsFabricCommonJar)
        from(relocatedPacketeventsFabricIntermediaryJar)
        from(relocatedPacketeventsFabricOfficialJar)
    }
}

// Create the v1_21_11 nested JAR from the dedicated module output (with PE relocations matching the main shadow JAR)
val fabric12111NestedJar by tasks.registering(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
    archiveBaseName.set("modl-fabric-12111")
    dependsOn(buildFabric12111)
    from(provider { if (fabric12111Jar.exists()) zipTree(fabric12111Jar) else files() })
    relocate("com.github.retrooper.packetevents", "gg.modl.libs.packetevents.api")
    relocate("io.github.retrooper.packetevents", "gg.modl.libs.packetevents.impl")
}

// Relocate PE references in the fabric-121 JAR
val fabric121RelocatedJar = layout.buildDirectory.file("fabric-121-relocated/modl-fabric-121.jar").map { it.asFile }

val relocateFabric121 by tasks.registering(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
    dependsOn(buildFabric121)
    archiveFileName.set("modl-fabric-121.jar")
    destinationDirectory.set(layout.buildDirectory.dir("fabric-121-relocated"))
    from(provider { if (fabric121Jar.exists()) zipTree(fabric121Jar) else files() })
    relocate("com.github.retrooper.packetevents", "gg.modl.libs.packetevents.api")
    relocate("io.github.retrooper.packetevents", "gg.modl.libs.packetevents.impl")
    onlyIf { fabric121Jar.exists() }
}

// Relocate PE references in the fabric-1214 JAR
val fabric1214RelocatedJar = layout.buildDirectory.file("fabric-1214-relocated/modl-fabric-1214.jar").map { it.asFile }

val relocateFabric1214 by tasks.registering(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
    dependsOn(buildFabric1214)
    archiveFileName.set("modl-fabric-1214.jar")
    destinationDirectory.set(layout.buildDirectory.dir("fabric-1214-relocated"))
    from(provider { if (fabric1214Jar.exists()) zipTree(fabric1214Jar) else files() })
    relocate("com.github.retrooper.packetevents", "gg.modl.libs.packetevents.api")
    relocate("io.github.retrooper.packetevents", "gg.modl.libs.packetevents.impl")
    onlyIf { fabric1214Jar.exists() }
}

// Relocate PE references in the fabric-26 JAR to match the main shadow JAR
val fabric26RelocatedJar = layout.buildDirectory.file("fabric-26-relocated/modl-fabric-26.jar").map { it.asFile }

val relocateFabric26 by tasks.registering(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
    dependsOn(buildFabric26)
    archiveFileName.set("modl-fabric-26.jar")
    destinationDirectory.set(layout.buildDirectory.dir("fabric-26-relocated"))
    from(provider { if (fabric26Jar.exists()) zipTree(fabric26Jar) else files() })
    relocate("com.github.retrooper.packetevents", "gg.modl.libs.packetevents.api")
    relocate("io.github.retrooper.packetevents", "gg.modl.libs.packetevents.impl")
    onlyIf { fabric26Jar.exists() }
}

tasks.shadowJar {
    dependsOn(":platforms:fabric:remapJar")
    dependsOn(packeteventsFabricNestedJar)
    dependsOn(fabric12111NestedJar)
    dependsOn(buildFabric121)
    dependsOn(relocateFabric121)
    dependsOn(buildFabric1214)
    dependsOn(relocateFabric1214)
    dependsOn(buildFabric26)
    dependsOn(relocateFabric26)
    archiveBaseName.set("modl")
    archiveClassifier.set("")

    relocate("com.github.retrooper.packetevents", "gg.modl.libs.packetevents.api")
    relocate("io.github.retrooper.packetevents", "gg.modl.libs.packetevents.impl")

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

    // Shell entry point + shell fabric.mod.json from Fabric Loom output
    from(zipTree(fabricLoomJar)) {
        include("gg/modl/minecraft/fabric/ModlFabricMod.class")
        include("fabric.mod.json")
        include("modl.accesswidener")
    }

    // Nested Fabric JARs — renamed to match fabric.mod.json "jars" declarations
    into("META-INF/jars") {
        from(packeteventsFabricNestedJar)
        from(fabric12111NestedJar) {
            rename(".*", "modl-fabric-12111.jar")
        }
        from(provider { if (fabric121RelocatedJar.get().exists()) listOf(fabric121RelocatedJar.get()) else emptyList() }) {
            rename(".*", "modl-fabric-121.jar")
        }
        from(provider { if (fabric1214RelocatedJar.get().exists()) listOf(fabric1214RelocatedJar.get()) else emptyList() }) {
            rename(".*", "modl-fabric-1214.jar")
        }
        from(provider { if (fabric26RelocatedJar.get().exists()) listOf(fabric26RelocatedJar.get()) else emptyList() }) {
            rename(".*", "modl-fabric-26.jar")
        }
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
