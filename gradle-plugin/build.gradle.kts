import org.gradle.plugin.compatibility.compatibility

plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "2.1.1"
}

description = "Gradle settings plugin for EnderFall SDK consumer projects"
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
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

gradlePlugin {
    website = "https://sdk.enderfall.co.uk"
    vcsUrl = "https://github.com/EnderFall-LTD/enderfall-sdk"
    plugins {
        create("enderfallSdk") {
            id = "uk.co.enderfall.sdk"
            implementationClass = "uk.co.enderfall.sdk.gradle.EnderfallSdkSettingsPlugin"
            displayName = "EnderFall SDK"
            description = "Build one portable Minecraft mod source set for supported loaders and versions."
            tags = listOf("minecraft", "fabric", "neoforge", "forge", "multiloader", "multiversion")
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

dependencies {
    implementation("org.gradle.toolchains.foojay-resolver-convention:org.gradle.toolchains.foojay-resolver-convention.gradle.plugin:1.0.0")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(gradleTestKit())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name == "pluginMaven") {
            artifactId = "enderfall-sdk-gradle-plugin"
        }
    }
}
