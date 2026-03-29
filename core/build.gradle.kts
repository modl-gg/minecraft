dependencies {
    api(project(":api"))
    compileOnly("com.google.guava:guava:32.0.1-jre")
    compileOnly("gg.modl.minecraft.cirrus:cirrus-api:${property("cirrus.version")}")
    compileOnly("co.aikar:acf-core:${property("acf.version")}")
    compileOnly("org.yaml:snakeyaml:${property("snakeyaml.version")}")
    compileOnly("org.apache.httpcomponents.client5:httpclient5:5.2.1")
    compileOnly("net.kyori:adventure-api:${property("adventure.version")}")
    compileOnly("net.kyori:adventure-text-minimessage:${property("adventure.version")}")
    compileOnly("net.kyori:adventure-text-serializer-legacy:${property("adventure.version")}")
    compileOnly("net.kyori:adventure-text-serializer-gson:${property("adventure.version")}")
    compileOnly("io.netty:netty-all:4.1.97.Final")
}

// PluginInfo.java template filtering (replaces Maven templating-maven-plugin)
val pluginProps = mapOf(
    "plugin" to mapOf(
        "id" to project.findProperty("plugin.id"),
        "name" to project.findProperty("plugin.name"),
        "version" to project.version,
        "author" to project.findProperty("plugin.author"),
        "description" to project.findProperty("plugin.description"),
        "url" to project.findProperty("plugin.url"),
    )
)

val generateTemplates = tasks.register<Copy>("generateTemplates") {
    from("src/main/java-templates")
    into(layout.buildDirectory.dir("generated/sources/templates/java/main"))
    expand(pluginProps)
    filteringCharset = "UTF-8"
}

sourceSets.main {
    java.srcDir(generateTemplates)
}
