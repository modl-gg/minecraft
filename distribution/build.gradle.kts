plugins {
    id("com.gradleup.shadow") version "9.3.1"
}

java {
    disableAutoTargetJvm()
}

val fabricLoomJar = project(":platforms:fabric").layout.buildDirectory.file("libs/fabric-${project.version}.jar")
val fabric26Jar = file("../platforms/fabric-26/build/libs/modl-fabric-26-${project.version}.jar")

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

// Create the v1_21 nested JAR from Loom output
val fabric121NestedJar by tasks.registering(Jar::class) {
    archiveBaseName.set("modl-fabric-121")
    dependsOn(":platforms:fabric:remapJar")
    from(zipTree(fabricLoomJar)) {
        include("gg/modl/minecraft/fabric/v1_21/**")
    }
    from(zipTree(fabricLoomJar)) {
        include("META-INF/impl-121-fabric.mod.json")
        rename("impl-121-fabric.mod.json", "fabric.mod.json")
    }
}

tasks.shadowJar {
    dependsOn(":platforms:fabric:remapJar")
    dependsOn(fabric121NestedJar)
    archiveBaseName.set("modl")
    archiveClassifier.set("")

    relocate("com.github.retrooper.packetevents", "gg.modl.libs.packetevents.api")
    relocate("io.github.retrooper.packetevents", "gg.modl.libs.packetevents.impl")

    exclude("**/module-info.class")
    exclude("com/google/gson/**")

    from(rootProject.file("LICENSE.txt"))

    // Shell entry point + shell fabric.mod.json from Fabric Loom output
    from(zipTree(fabricLoomJar)) {
        include("gg/modl/minecraft/fabric/ModlFabricMod.class")
        include("fabric.mod.json")
    }

    // Nested Fabric JARs — included as actual JAR files inside META-INF/jars/
    into("META-INF/jars") {
        from(fabric121NestedJar)
        if (fabric26Jar.exists()) {
            from(fabric26Jar)
        }
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
