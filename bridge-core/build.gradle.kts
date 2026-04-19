dependencies {
    api(project(":core"))
    api(project(":api"))
    compileOnly("org.yaml:snakeyaml:${property("snakeyaml.version")}")
    compileOnly("net.kyori:adventure-text-minimessage:${property("adventure.version")}")
    compileOnly("net.kyori:adventure-text-serializer-legacy:${property("adventure.version")}")
    compileOnly("io.netty:netty-all:4.1.97.Final")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.google.code.gson:gson:${property("gson.version")}")
    testImplementation("org.yaml:snakeyaml:${property("snakeyaml.version")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
