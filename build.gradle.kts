import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    java
}

val sharedConfigExclusions = setOf("fabric", "platforms")

val defaultJvmRelease = 8
val defaultToolchainVersion = 21

val jvmReleaseOverrides = mapOf(
    "velocity" to 25,
    "spigot-sv" to 21,
)

allprojects {
    group = property("group")!!
    version = property("version")!!
}

subprojects {
    if (name in sharedConfigExclusions) return@subprojects

    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    repositories {
        maven("https://nexus.modl.gg/repository/maven-releases/")
        maven("https://nexus.modl.gg/repository/maven-snapshots/")
        mavenCentral()
        maven("https://repo.aikar.co/content/groups/aikar/")
        maven("https://jitpack.io")
        maven("https://repo.codemc.io/repository/maven-releases/")
        maven("https://repo.codemc.io/repository/maven-snapshots/")
        maven("https://repo.alessiodp.com/snapshots/")
        maven("https://oss.sonatype.org/content/repositories/snapshots")
    }

    val javaRelease = jvmReleaseOverrides[name] ?: defaultJvmRelease

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(maxOf(defaultToolchainVersion, javaRelease)))
        }
    }

    val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
    fun sharedLibrary(alias: String) = libs.findLibrary(alias).orElseThrow {
        GradleException("Missing library alias '$alias' in libs.versions.toml")
    }

    dependencies {
        "compileOnly"(sharedLibrary("lombok"))
        "annotationProcessor"(sharedLibrary("lombok"))
        "compileOnly"(sharedLibrary("slf4j-api"))
        "compileOnly"(sharedLibrary("gson"))
        "compileOnly"(sharedLibrary("jetbrains-annotations"))
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
        options.compilerArgs.add("-Xlint:-options")
    }

    tasks.named<JavaCompile>("compileJava") {
        options.release.set(javaRelease)
    }
}
