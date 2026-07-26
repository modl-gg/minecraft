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

val velocity3ApiLinePrefix = "3."

val verifyVelocityApiLine by tasks.registering {
    description = "Fails unless velocity-api stays on the 3.x line, so one jar keeps resolving on Velocity 3 and 4"
    group = "verification"
    val pinnedVelocityVersion = libs.versions.velocity.get()
    inputs.property("pinnedVelocityVersion", pinnedVelocityVersion)
    outputs.upToDateWhen { true }

    doLast {
        check(pinnedVelocityVersion.startsWith(velocity3ApiLinePrefix)) {
            "platforms/velocity must compile against the Velocity ${velocity3ApiLinePrefix}x API so a single jar " +
                "resolves on both Velocity 3 and Velocity 4 proxies, but gradle/libs.versions.toml pins " +
                "velocity = \"$pinnedVelocityVersion\". Velocity 4 kept every API member this module uses, so the " +
                "3.x API is the compatible floor; raising it drops Velocity 3 support."
        }
    }
}

tasks.check {
    dependsOn(verifyVelocityApiLine)
}
