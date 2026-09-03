pluginManagement {
    repositories {
        maven { url = uri("../build/repository") }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("uk.co.enderfall.sdk") version "0.1.0-beta.1"
}

dependencyResolutionManagement {
    repositories {
        maven { url = uri("../build/repository") }
        mavenCentral()
    }
}

rootProject.name = "enderfall-sdk-contract-test"

enderfallSdk {
    mod {
        id = "enderfall_sdk_test"
        name = "EnderFall SDK Contract Test"
        group = "uk.co.enderfall.sdk.testmod"
        version = "0.1.0-beta.1"
        entrypoint = "uk.co.enderfall.sdk.testmod.ContractTestMod"
        clientEntrypoint = "uk.co.enderfall.sdk.testmod.ContractTestClient"
        author = "EnderFall"
        license = "Apache-2.0"
    }

    targets {
        version("1.20.1") { loaders("fabric", "forge", "neoforge") }
        version("1.21.1") { loaders("fabric", "neoforge") }
        version("1.21.4") { loaders("fabric", "neoforge") }
        version("26.2") { loaders("fabric", "neoforge") }
    }

    developmentTarget = "26.2-fabric"
}
