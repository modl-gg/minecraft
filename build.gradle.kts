plugins {
    java
}

val modPlatformModules = setOf("fabric")

allprojects {
    group = property("group")!!
    version = property("version")!!
}

subprojects {
    if (name in modPlatformModules) return@subprojects

    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    repositories {
        maven("https://nexus.modl.gg/repository/maven-releases/")
        mavenCentral()
        maven("https://repo.aikar.co/content/groups/aikar/")
        maven("https://jitpack.io")
        maven("https://repo.codemc.io/repository/maven-releases/")
        maven("https://repo.codemc.io/repository/maven-snapshots/")
        maven("https://repo.alessiodp.com/snapshots/")
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        mavenLocal()
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    dependencies {
        "compileOnly"("org.projectlombok:lombok:${property("lombok.version")}")
        "annotationProcessor"("org.projectlombok:lombok:${property("lombok.version")}")
        "compileOnly"("org.slf4j:slf4j-api:${property("slf4j.version")}")
        "compileOnly"("com.google.code.gson:gson:${property("gson.version")}")
        "compileOnly"("org.jetbrains:annotations:13.0")
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
