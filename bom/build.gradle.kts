plugins {
    `java-platform`
    `maven-publish`
}

description = "Coordinated dependency versions for EnderFall SDK"

dependencies {
    constraints {
        api("uk.co.enderfall.sdk:enderfall-sdk-api:${project.version}")
        api("uk.co.enderfall.sdk:enderfall-sdk-runtime-core:${project.version}")
        api("uk.co.enderfall.sdk:enderfall-sdk-gradle-plugin:${project.version}")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenBom") {
            artifactId = "enderfall-sdk-bom"
            from(components["javaPlatform"])
            pom {
                name = "EnderFall SDK BOM"
                description = project.description
                url = "https://github.com/EnderFall/enderfall-sdk"
            }
        }
    }
}
