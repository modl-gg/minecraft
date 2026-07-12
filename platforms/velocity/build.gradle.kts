repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
    annotationProcessor(libs.lombok)

    implementation(project(":core"))

    compileOnly(libs.lamp.common)
    compileOnly(libs.lamp.brigadier)
    compileOnly(libs.lamp.velocity)
    compileOnly(libs.cirrus.api)
    compileOnly(libs.cirrus.velocity)
    compileOnly(libs.snakeyaml)

    implementation(libs.libby.core)
    implementation(libs.libby.velocity)

    compileOnly(libs.packetevents.api)
    compileOnly(libs.packetevents.velocity)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "gg.modl.minecraft.platform.velocity.plugin")
    }
}

tasks.test {
    useJUnitPlatform()
}
