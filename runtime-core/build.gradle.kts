plugins {
    `java-library`
    `maven-publish`
    id("com.gradleup.shadow") version "9.6.1"
}

description = "Shared runtime implementation for EnderFall SDK target adapters"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
    withSourcesJar()
    withJavadocJar()
}

tasks.shadowJar {
    archiveClassifier = ""
    relocate("org.tomlj", "uk.co.enderfall.sdk.internal.tomlj")
}

tasks.jar {
    archiveClassifier = "plain"
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

publishing {
    publications {
        create<MavenPublication>("mavenRuntime") {
            artifactId = "enderfall-sdk-runtime-core"
            artifact(tasks.shadowJar)
            artifact(tasks.named<Jar>("sourcesJar"))
            artifact(tasks.named<Jar>("javadocJar"))
            pom {
                name = "EnderFall SDK Runtime Core"
                description = project.description
                url = "https://github.com/EnderFall/enderfall-sdk"
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
            }
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

dependencies {
    api(project(":api"))
    implementation("org.tomlj:tomlj:1.1.1")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
