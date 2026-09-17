pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
            content {
                includeGroup("fabric-loom")
                includeGroupByRegex("net\\.fabricmc(\\..*)?")
            }
        }
        maven("https://maven.neoforged.net/releases") {
            name = "NeoForged"
            content {
                includeGroupByRegex("net\\.neoforged(\\..*)?")
                includeGroupByRegex("net\\.minecraftforge(\\..*)?")
                includeGroupByRegex("cpw\\.mods(\\..*)?")
                includeGroupByRegex("de\\.oceanlabs(\\..*)?")
                includeGroupByRegex("org\\.spongepowered(\\..*)?")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    // Loom and ModDevGradle add generated/local remapping repositories to target projects.
    // Dependency verification still pins every resolved artifact by SHA-256.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
            content {
                includeGroup("fabric-loom")
                includeGroupByRegex("net\\.fabricmc(\\..*)?")
            }
        }
        maven("https://maven.neoforged.net/releases") {
            name = "NeoForged"
            content {
                includeGroupByRegex("net\\.neoforged(\\..*)?")
                includeGroupByRegex("net\\.minecraftforge(\\..*)?")
                includeGroupByRegex("cpw\\.mods(\\..*)?")
                includeGroupByRegex("de\\.oceanlabs(\\..*)?")
                includeGroupByRegex("org\\.spongepowered(\\..*)?")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "enderfall-sdk"

include(
    "bom",
    "api",
    "bridge-compiler",
    "runtime-core",
    "gradle-plugin",
    "integration-harness",
    "test-mod"
)

val referenceRuntimes = providers.gradleProperty("enderfall.referenceRuntimes").orNull ?: "true"
require(referenceRuntimes in listOf("true", "false")) { "enderfall.referenceRuntimes must be true or false" }
if (referenceRuntimes == "true") include(
    "runtime-fabric-1.20.1",
    "runtime-fabric-1.21.1",
    "runtime-fabric-1.21.4",
    "runtime-fabric-26.2",
    "runtime-forge-1.20.1",
    "runtime-neoforge-1.20.1",
    "runtime-neoforge-1.21.4",
    "runtime-neoforge-1.21.1",
    "runtime-neoforge-26.2"
)

// Project descriptors point at shared family scripts, never target-owned code.
// Keep project IDs/output roots stable while reference parity checks remain active.
val runtimeFamilies = mapOf(
    "fabric-remapped" to "fabric-remapped.gradle.kts",
    "fabric-unobfuscated" to "fabric-unobfuscated.gradle.kts",
    "legacy-fml" to "legacy-fml.gradle",
    "neoforge" to "neoforge.gradle"
)
val runtimeProjects = sortedMapOf<String, String>()
providers.fileContents(layout.settingsDirectory.file("gradle/runtime-projects.properties"))
    .asText.get().lineSequence().map(String::trim)
    .filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
        val parts = line.split('=')
        require(parts.size == 2) { "Invalid runtime project entry: $line" }
        val id = parts[0].trim()
        val family = parts[1].trim()
        require(id.matches(Regex("[0-9]+(?:\\.[0-9]+){1,2}-(fabric|forge|neoforge)"))) {
            "Unsafe runtime target ID: $id"
        }
        require(family in runtimeFamilies) { "Unknown runtime build family: $family" }
        require(runtimeProjects.put(id, family) == null) { "Duplicate runtime target: $id" }
    }
require(runtimeProjects.isNotEmpty()) { "Runtime project catalog is empty" }
runtimeProjects.forEach { (id, family) ->
    val minecraft = id.substringBeforeLast('-')
    val loader = id.substringAfterLast('-')
    val projectName = "runtime-generated-$loader-$minecraft"
    val directory = layout.settingsDirectory.dir(projectName).asFile
    require(directory.isDirectory || directory.mkdirs()) { "Cannot create runtime output root: $directory" }
    include(projectName)
    project(":$projectName").buildFileName = "../gradle/targets/${runtimeFamilies.getValue(family)}"
}
