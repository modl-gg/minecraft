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

fun existingJar(file: java.io.File) = provider {
    if (file.exists()) listOf(file) else emptyList()
}

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
}

tasks.shadowJar {
    dependsOn(":platforms:fabric:remapJar")
    dependsOn(buildFabric12111)
    dependsOn(buildFabric121)
    dependsOn(buildFabric1214)
    dependsOn(buildFabric26)
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
        from(existingJar(fabric12111Jar)) {
            rename(".*", "modl-fabric-12111.jar")
        }
        from(existingJar(fabric121Jar)) {
            rename(".*", "modl-fabric-121.jar")
        }
        from(existingJar(fabric1214Jar)) {
            rename(".*", "modl-fabric-1214.jar")
        }
        from(existingJar(fabric26Jar)) {
            rename(".*", "modl-fabric-26.jar")
        }
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
