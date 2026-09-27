import java.util.Properties

plugins {
    alias(libs.plugins.fabric.loom)
}

val archivesBaseName = providers.gradleProperty("archives_base_name").get()
val mavenGroup = providers.gradleProperty("maven_group").get()

base {
    archivesName = archivesBaseName
}

// version/group are Project properties, not members of BasePluginExtension - they don't
// belong inside base { } (that was quietly relying on outer-scope lookup and is what a
// couple of the "Assignment type mismatch" errors were pointing at).
//
// Auto-incrementing version, stored in version.properties (created automatically, git-ignore
// it if you don't want it committed): each build bumps patch by 1; once patch hits 10 it
// resets to 0 and minor goes up by 1; once minor hits 10 it resets to 0 and major goes up by
// 1. The output jar ends up named "<archivesBaseName>-<major>.<minor>.<patch>.jar" since Loom
// already combines base.archivesName with project.version for you.
//
// Note this re-evaluates on every Gradle invocation that configures this project (not just
// `build`), so things like `gradlew tasks` will also bump the counter - that's normal for
// this kind of setup, just don't run random Gradle commands if you want the counter to only
// move on real builds.
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
    // Fabric
    minecraft(libs.minecraft)
    // 1.21.11 is still obfuscated, so it needs Yarn mappings - this was missing entirely,
    // which is why Loom couldn't produce named classes for Efly/SixToolsAddon to compile against.
    mappings(variantOf(libs.yarn) { classifier("v2") })
    // Mod dependencies need modImplementation (not implementation) so Loom remaps them
    // to match your Yarn mappings instead of leaving them in intermediary names.
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)

    // Meteor
    modImplementation(libs.meteor.client)

    // StashMover (ported from BepHaxAddon) paths via Baritone (baritone-1.21.11, group "baritone",
    // artifact "baritone-fabric", version 1.17.0 per its gradle.properties). This fork isn't published
    // to any Maven repo, so it's included here as a local jar in libs/ instead - since it's a real
    // Fabric mod jar (has fabric.mod.json + mixins), it needs modImplementation, not plain implementation,
    // so Loom remaps it to match this project's Yarn mappings.
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
