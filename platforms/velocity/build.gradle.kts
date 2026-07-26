// One jar serves both Velocity 3 and Velocity 4, so both settings target the *older* proxy.
// Velocity 4 requires Java 25 and ships velocity-api built for it, but Java 17 bytecode loads
// fine there, whereas Java 25 bytecode cannot load on a Velocity 3 proxy running Java 17.
// Compiling against the 3.x API is safe for the same reason: 4.x neither removed nor changed
// any API member this module uses, so v3-compiled calls resolve on both.
// VelocityCompatibilityTest pins both halves of this.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:${property("velocity.version")}")
    annotationProcessor("com.velocitypowered:velocity-api:${property("velocity.version")}")
    annotationProcessor("org.projectlombok:lombok:${property("lombok.version")}")

    implementation(project(":core"))

    compileOnly("io.github.revxrsal:lamp.common:${property("lamp.version")}")
    compileOnly("io.github.revxrsal:lamp.brigadier:${property("lamp.version")}")
    compileOnly("io.github.revxrsal:lamp.velocity:${property("lamp.version")}")
    compileOnly("gg.modl.minecraft.cirrus:cirrus-api:${property("cirrus.version")}")
    compileOnly("gg.modl.minecraft.cirrus:cirrus-velocity:${property("cirrus.version")}")
    compileOnly("org.yaml:snakeyaml:${property("snakeyaml.version")}")

    implementation("com.alessiodp.libby:libby-core:${property("libby.version")}")
    implementation("com.alessiodp.libby:libby-velocity:${property("libby.version")}")

    compileOnly("gg.modl.minecraft.packetevents:packetevents-api:${property("packetevents.version")}")
    compileOnly("gg.modl.minecraft.packetevents:packetevents-velocity:${property("packetevents.version")}")

    testImplementation(platform("org.junit:junit-bom:${property("junit.bom.version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "gg.modl.minecraft.platform.velocity.plugin")
    }
}

tasks.test {
    useJUnitPlatform()
}
