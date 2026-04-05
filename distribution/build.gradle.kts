plugins {
    id("com.gradleup.shadow") version "9.3.1"
}

java {
    disableAutoTargetJvm()
}

val fabricLoomJar = project(":platforms:fabric").layout.buildDirectory.file("libs/fabric-${project.version}.jar")
val fabric26Dir = rootProject.file("platforms/fabric-26")
val fabric26Jar = fabric26Dir.resolve("build/libs/modl-fabric-26-${project.version}.jar")

val buildFabric26 by tasks.registering(Exec::class) {
    description = "Builds the Fabric 26.1 module (separate Gradle build due to Loom version conflict)"
    dependsOn(":core:jar", ":bridge-core:jar", ":api:jar")
    workingDir = fabric26Dir
    commandLine(
        fabric26Dir.resolve("gradlew").absolutePath,
        "build", "-x", "test"
    )
    onlyIf { fabric26Dir.resolve("build.gradle").exists() }
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
}

// Create the v1_21 nested JAR from Loom output (with PE relocations matching the main shadow JAR)
val fabric121NestedJar by tasks.registering(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
    archiveBaseName.set("modl-fabric-121")
    dependsOn(":platforms:fabric:remapJar")
    from(zipTree(fabricLoomJar)) {
        include("gg/modl/minecraft/fabric/v1_21/**")
    }
    into("") {
        from(zipTree(fabricLoomJar)) {
            include("META-INF/impl-121-fabric.mod.json")
        }
        eachFile {
            relativePath = RelativePath(true, "fabric.mod.json")
        }
        includeEmptyDirs = false
    }
    relocate("com.github.retrooper.packetevents", "gg.modl.libs.packetevents.api")
    relocate("io.github.retrooper.packetevents", "gg.modl.libs.packetevents.impl")
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
    dependsOn(fabric121NestedJar)
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
        from(fabric121NestedJar) {
            rename(".*", "modl-fabric-121.jar")
        }
        from(provider { if (fabric26RelocatedJar.get().exists()) listOf(fabric26RelocatedJar.get()) else emptyList() }) {
            rename(".*", "modl-fabric-26.jar")
        }
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
