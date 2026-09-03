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
    val apiProject = findProject(":api")
    if (apiProject != null) {
        implementation(apiProject)
    } else {
        implementation("uk.co.enderfall.sdk:enderfall-sdk-api:0.1.0-beta.1")
    }
}
