plugins {
    id("com.gradleup.shadow") version "9.3.1"
}

// Distribution bundles modules with different Java targets (8, 17)
// Disable auto-target-JVM to allow mixing
java {
    disableAutoTargetJvm()
}

dependencies {
    implementation(project(":core"))
    implementation(project(":bridge-core"))
    implementation(project(":platforms:spigot"))
    implementation(project(":platforms:spigot-sv"))
    implementation(project(":platforms:velocity"))
    implementation(project(":platforms:bungee"))
    implementation(project(":platforms:fabric")) {
        isTransitive = false
    }
    implementation(project(":platforms:neoforge")) {
        isTransitive = false
    }
}

tasks.shadowJar {
    archiveBaseName.set("modl")
    archiveClassifier.set("")

    relocate("com.github.retrooper.packetevents", "gg.modl.libs.packetevents.api")
    relocate("io.github.retrooper.packetevents", "gg.modl.libs.packetevents.impl")

    exclude("**/module-info.class")

    from(rootProject.file("LICENSE.txt"))
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
