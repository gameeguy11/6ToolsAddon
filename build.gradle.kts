import java.util.Properties

plugins {
    alias(libs.plugins.fabric.loom)
}

val archivesBaseName = providers.gradleProperty("archives_base_name").get()
val mavenGroup = providers.gradleProperty("maven_group").get()

base {
    archivesName = archivesBaseName
}

val versionFile = file("version.properties")
val versionProps = Properties()
if (versionFile.exists()) {
    versionFile.inputStream().use { versionProps.load(it) }
}

var verMajor = (versionProps.getProperty("major") ?: "0").toInt()
var verMinor = (versionProps.getProperty("minor") ?: "0").toInt()
var verPatch = (versionProps.getProperty("patch") ?: "0").toInt()

verPatch++
if (verPatch >= 10) {
    verPatch = 0
    verMinor++
    if (verMinor >= 10) {
        verMinor = 0
        verMajor++
    }
}

versionProps.setProperty("major", verMajor.toString())
versionProps.setProperty("minor", verMinor.toString())
versionProps.setProperty("patch", verPatch.toString())
versionFile.outputStream().use {
    versionProps.store(it, "Auto-incremented build version - edit these numbers by hand if you want to reset/bump the counter.")
}

version = "$verMajor.$verMinor.$verPatch"
group = mavenGroup

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
}

dependencies {

    minecraft(libs.minecraft)

    mappings(variantOf(libs.yarn) { classifier("v2") })

    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)

    modImplementation(libs.meteor.client)

    modImplementation(files("libs/baritone-fabric-1.17.0.jar"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get().toInt()))
    }
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "minecraft_version" to libs.versions.minecraft.get(),
            "jdk_version" to libs.versions.jdk.get(),
            "loader_version" to libs.versions.fabric.loader.get(),
        )

        inputs.properties(propertyMap)
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(
            listOf(
                "-Xlint:deprecation",
                "-Xlint:unchecked"
            )
        )
    }
}
