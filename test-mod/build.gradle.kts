plugins {
    java
}

description = "Portable API compile fixture used by the EnderFall SDK contract tests"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

dependencies {
    implementation(project(":api"))
}
