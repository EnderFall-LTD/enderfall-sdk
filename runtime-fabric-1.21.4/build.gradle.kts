plugins {
    `java-library`
    `maven-publish`
    id("net.fabricmc.fabric-loom-remap") version "1.17.20"
}

description = "EnderFall SDK runtime adapter for Minecraft 1.21.4 on Fabric"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
    withJavadocJar()
}

loom {
    runs {
        named("client") {
            runDir = "run/client"
        }
        named("server") {
            runDir = "run/server"
            programArgs("nogui")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.4")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.19.5")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.119.4+1.21.4")

    compileOnly(project(":runtime-core"))
    include(project(path = ":runtime-core", configuration = "shadowRuntimeElements"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

val sdkArtifactVersion = version.toString()
tasks.processResources {
    inputs.property("version", sdkArtifactVersion)
    expand(mapOf("version" to sdkArtifactVersion))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "enderfall-sdk-runtime-1.21.4-fabric"
            from(components["java"])
        }
    }
}
