plugins {
    `java-library`
    application
}

description = "Deterministic target bridge generator for EnderFall SDK runtimes"
group = "uk.co.enderfall.sdk"
version = providers.gradleProperty("sdkVersion").getOrElse("0.1.0-beta.1")

repositories {
    mavenCentral()
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
    withSourcesJar()
    withJavadocJar()
}

application {
    mainClass = "uk.co.enderfall.sdk.bridge.BridgeCompilerMain"
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    inputs.dir(rootProject.layout.projectDirectory.dir("bridge-runtime"))
    inputs.file(rootProject.layout.projectDirectory.file("gradle/runtime-projects.properties"))
    inputs.files(rootProject.fileTree("gradle/targets") { include("*.gradle", "*.gradle.kts") })
    inputs.files(rootProject.subprojects.filter { it.name.startsWith("runtime-generated-") }
        .flatMap { listOf(it.file("build.gradle"), it.file("build.gradle.kts")) })
}

tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    manifest.attributes["Main-Class"] = application.mainClass.get()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
