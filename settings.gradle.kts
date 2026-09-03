pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        maven("https://maven.neoforged.net/releases") {
            name = "NeoForged"
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
        }
        maven("https://maven.neoforged.net/releases") {
            name = "NeoForged"
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "enderfall-sdk"

include(
    "bom",
    "api",
    "runtime-core",
    "runtime-fabric-1.20.1",
    "runtime-fabric-1.21.1",
    "runtime-fabric-1.21.4",
    "runtime-fabric-26.2",
    "runtime-forge-1.20.1",
    "runtime-neoforge-1.20.1",
    "runtime-neoforge-1.21.4",
    "runtime-neoforge-1.21.1",
    "runtime-neoforge-26.2",
    "gradle-plugin",
    "test-mod"
)
