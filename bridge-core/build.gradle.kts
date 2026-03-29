dependencies {
    api(project(":core"))
    api(project(":api"))
    compileOnly("org.yaml:snakeyaml:${property("snakeyaml.version")}")
    compileOnly("net.kyori:adventure-text-minimessage:${property("adventure.version")}")
    compileOnly("net.kyori:adventure-text-serializer-legacy:${property("adventure.version")}")
    compileOnly("io.netty:netty-all:4.1.97.Final")
}
