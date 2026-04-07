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
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "gg.modl.minecraft.platform.velocity.plugin")
    }
}
