dependencies {
    api(project(":core"))
    api(project(":api"))
    compileOnly(libs.snakeyaml)
    compileOnly(libs.adventure.minimessage)
    compileOnly(libs.adventure.serializer.legacy)
    compileOnly(libs.adventure.api)
    compileOnly(libs.netty.all)
    compileOnly(libs.packetevents.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.gson)
    testImplementation(libs.snakeyaml)
    testImplementation(libs.netty.all)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
