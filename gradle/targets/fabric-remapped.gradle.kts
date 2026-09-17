plugins {
    `java-library`
    id("net.fabricmc.fabric-loom-remap") version "1.17.20"
}

extra["enderfallGeneratedFabricTarget"] = name.removePrefix("runtime-generated-fabric-") + "-fabric"
apply(from = rootProject.file("gradle/generated-fabric-runtime.gradle.kts"))
