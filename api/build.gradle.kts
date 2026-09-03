plugins {
    `java-library`
    `maven-publish`
}

description = "Loader-neutral Java 17 API for EnderFall SDK mods"
group = "uk.co.enderfall.sdk"
version = providers.gradleProperty("sdkVersion").getOrElse("0.1.0-beta.1")

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "enderfall-sdk-api"
            from(components["java"])
            pom {
                name = "EnderFall SDK API"
                description = project.description
                url = "https://github.com/EnderFall/enderfall-sdk"
            }
        }
    }
}
