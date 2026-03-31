java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    maven("https://nexus.velocitypowered.com/repository/maven-public/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:${property("velocity.version")}")
    annotationProcessor("com.velocitypowered:velocity-api:${property("velocity.version")}")
    annotationProcessor("org.projectlombok:lombok:${property("lombok.version")}")

    implementation(project(":core"))

    compileOnly("co.aikar:acf-velocity:${property("acf.version")}")
    compileOnly("gg.modl.minecraft.cirrus:cirrus-api:${property("cirrus.version")}")
    compileOnly("gg.modl.minecraft.cirrus:cirrus-velocity:${property("cirrus.version")}")
    compileOnly("org.yaml:snakeyaml:${property("snakeyaml.version")}")

    implementation("com.alessiodp.libby:libby-core:${property("libby.version")}")
    implementation("com.alessiodp.libby:libby-velocity:${property("libby.version")}")

    compileOnly("com.github.retrooper:packetevents-api:${property("packetevents.version")}")
    compileOnly("com.github.retrooper:packetevents-velocity:${property("packetevents.version")}")
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "gg.modl.minecraft.platform.velocity.plugin")
    }
}
