pluginManagement {
    repositories {
        maven {
            url = uri(providers.gradleProperty("enderfall.workspaceRepository")
                .getOrElse("../../build/repository"))
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
                .getOrElse("../../build/repository"))
        }
        mavenCentral()
    }
}

rootProject.name = "enderfall-sdk-demo"

enderfallSdk {
    mod {
        id = "enderfall_sdk_demo"
        name = "EnderFall SDK Demo"
        group = "uk.co.enderfall.sdk.demo"
        version = "0.1.0"
        entrypoint = "uk.co.enderfall.sdk.demo.DemoMod"
        clientEntrypoint = "uk.co.enderfall.sdk.demo.DemoClient"
        author = "EnderFall"
        license = "CC0-1.0"
    }

    targets {
        version("1.20.1") { loaders("fabric", "forge", "neoforge") }
        version("1.21.1") { loaders("fabric", "neoforge") }
        version("1.21.4") { loaders("fabric", "neoforge") }
        version("26.2") { loaders("fabric", "neoforge") }
    }

    developmentTarget = "26.2-fabric"
}
