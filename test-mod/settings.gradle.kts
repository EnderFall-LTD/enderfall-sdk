pluginManagement {
    repositories {
        maven {
            url = uri(providers.gradleProperty("enderfall.workspaceRepository")
                .getOrElse("../build/repository"))
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("uk.co.enderfall.sdk") version "0.1.0-beta.1"
}

dependencyResolutionManagement {
    repositories {
        maven {
            url = uri(providers.gradleProperty("enderfall.workspaceRepository")
                .getOrElse("../build/repository"))
        }
        mavenCentral()
    }
}

rootProject.name = "enderfall-sdk-contract-test"

// The process-level smoke harness selects one target so two concurrently running
// Gradle builds do not configure unrelated Loom/ModDevGradle projects. Normal
// contract-test builds omit this internal property and retain the full matrix.
val fixtureTarget = providers.gradleProperty("enderfall.fixtureTarget").orNull
val gameplayProbe = providers.gradleProperty("enderfall.gameplayProbe").getOrElse("false").toBoolean()

enderfallSdk {
    mod {
        id = "enderfall_sdk_test"
        name = "EnderFall SDK Contract Test"
        group = "uk.co.enderfall.sdk.testmod"
        version = "0.1.0-beta.1"
        entrypoint = "uk.co.enderfall.sdk.testmod.ContractTestMod"
        clientEntrypoint = if (gameplayProbe) "uk.co.enderfall.sdk.testmod.NativeGameplayClient"
            else "uk.co.enderfall.sdk.testmod.ContractTestClient"
        author = "EnderFall"
        license = "Apache-2.0"
    }

    targets {
        if (fixtureTarget == null) {
            version("1.20.1") { loaders("fabric", "forge", "neoforge") }
            version("1.21.1") { loaders("fabric", "neoforge") }
            version("1.21.4") { loaders("fabric", "neoforge") }
            version("26.2") { loaders("fabric", "neoforge") }
        } else {
            val separator = fixtureTarget.lastIndexOf('-')
            require(separator > 0 && separator + 1 < fixtureTarget.length) {
                "Invalid EnderFall fixture target '$fixtureTarget'"
            }
            version(fixtureTarget.substring(0, separator)) {
                loaders(fixtureTarget.substring(separator + 1))
            }
        }
    }

    developmentTarget = fixtureTarget ?: "26.2-fabric"
}
