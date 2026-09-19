plugins {
    `java-library`
    `maven-publish`
    id("net.fabricmc.fabric-loom") version "1.17.20"
}

description = "EnderFall SDK runtime adapter for Minecraft 26.2 on Fabric"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
    withJavadocJar()
}

sourceSets.main {
    java.srcDir(project(":runtime-fabric-1.21.4").file("src/main/java"))
    java.exclude("**/FabricPlatformAdapter.java", "**/FabricCommandBridge.java",
        "**/FabricPortableMenuScreen.java", "**/FabricWorkbenchRecipe.java",
        "**/FabricWorkbenchMenu.java", "**/FabricWorkbenchScreen.java")
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
    minecraft("com.mojang:minecraft:26.2")
    implementation("net.fabricmc:fabric-loader:0.19.5")
    implementation("net.fabricmc.fabric-api:fabric-api:0.141.6+")

    compileOnly(project(":runtime-core"))
    include(project(path = ":runtime-core", configuration = "shadowRuntimeElements"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    // The two version-specific package-private replacements intentionally use distinct filenames.
    options.compilerArgs.add("-Xlint:-auxiliaryclass")
}

val sdkArtifactVersion = version.toString()
tasks.processResources {
    inputs.property("version", sdkArtifactVersion)
    expand(mapOf("version" to sdkArtifactVersion))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "enderfall-sdk-runtime-26.2-fabric"
            from(components["java"])
        }
    }
}
