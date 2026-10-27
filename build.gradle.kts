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
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
    mavenCentral()
}

loom {
    accessWidenerPath = file("src/main/resources/sixtoolsaddon.accesswidener")
}

dependencies {

    minecraft(libs.minecraft)

    mappings(variantOf(libs.yarn) { classifier("v2") })

    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)

    modImplementation(libs.meteor.client)

    modCompileOnly("maven.modrinth:xaeroplus:2.30.9+fabric-1.21.11")
    modCompileOnly("maven.modrinth:xaeros-world-map:fabric-1.21.11-1.40.11")
    modCompileOnly("maven.modrinth:xaeros-minimap:fabric-1.21.11-25.3.10")
    compileOnly("net.lenni0451:LambdaEvents:2.4.2")
    compileOnly("com.github.ben-manes.caffeine:caffeine:3.1.8")

    val preferredBaritone = file("libs/baritone-api-fabric-1.3.0-1.21.11.jar")
    val baritoneJar = if (preferredBaritone.exists()) preferredBaritone else file("libs").listFiles { f -> f.isFile && f.name.startsWith("baritone") && f.name.endsWith(".jar") }?.sortedByDescending { it.name }?.firstOrNull()
    if (baritoneJar == null) {
        throw GradleException("No Baritone API jar found in libs/. Put baritone-api-fabric-1.3.0-1.21.11.jar there (https://github.com/dekrom/baritone/releases/tag/v1.3.0-1.21.11).")
    }
    modCompileOnly(files(baritoneJar))
    modLocalRuntime(files(baritoneJar))
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

        val gitCommit = try {
            val proc = ProcessBuilder("git", "rev-parse", "HEAD").directory(projectDir).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText().trim()
            if (proc.waitFor() == 0 && out.matches(Regex("[0-9a-f]{40}"))) out else ""
        } catch (e: Exception) {
            ""
        }
        inputs.property("gitCommit", gitCommit)
        doLast {
            File(destinationDir, "sixtools-build.properties").writeText("commit=$gitCommit\n")
        }
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
