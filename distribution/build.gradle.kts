plugins {
    id("com.gradleup.shadow") version "9.3.1"
}

java {
    disableAutoTargetJvm()
}

val fabricJar = project(":platforms:fabric").layout.buildDirectory.file("libs/fabric-${project.version}.jar")

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
    archiveBaseName.set("modl")
    archiveClassifier.set("")

    relocate("com.github.retrooper.packetevents", "gg.modl.libs.packetevents.api")
    relocate("io.github.retrooper.packetevents", "gg.modl.libs.packetevents.impl")

    exclude("**/module-info.class")
    exclude("com/google/gson/**")

    from(rootProject.file("LICENSE.txt"))
    // Include Loom-remapped Fabric classes (intermediary-mapped for runtime)
    from(zipTree(fabricJar))
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
