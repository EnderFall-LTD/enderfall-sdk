import java.security.MessageDigest

plugins {
    base
    id("org.cyclonedx.bom") version "3.4.1"
}

val workspaceRepository = layout.buildDirectory.dir("repository")
val japicmp = configurations.create("japicmp") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(
            org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE,
            objects.named(org.gradle.api.attributes.Category.LIBRARY)
        )
        attribute(
            org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE,
            objects.named(org.gradle.api.attributes.Usage.JAVA_RUNTIME)
        )
        attribute(
            org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
            objects.named(org.gradle.api.attributes.LibraryElements.JAR)
        )
        attribute(
            org.gradle.api.attributes.Bundling.BUNDLING_ATTRIBUTE,
            objects.named(org.gradle.api.attributes.Bundling.EXTERNAL)
        )
        attribute(
            org.gradle.api.attributes.java.TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
            objects.named(org.gradle.api.attributes.java.TargetJvmEnvironment.STANDARD_JVM)
        )
    }
}

dependencies {
    japicmp("com.github.siom79.japicmp:japicmp:0.26.1")
}

group = "uk.co.enderfall.sdk"
version = providers.gradleProperty("sdkVersion").get()

subprojects {
    group = rootProject.group
    version = rootProject.version

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    tasks.withType<Jar>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        from(rootProject.file("LICENSE")) {
            into("META-INF")
            rename { "LICENSE-enderfall-sdk" }
        }
        from(rootProject.file("NOTICE")) {
            into("META-INF")
            rename { "NOTICE-enderfall-sdk" }
        }
        from(rootProject.file("THIRD-PARTY-NOTICES.md")) {
            into("META-INF")
            rename { "THIRD-PARTY-NOTICES-enderfall-sdk.md" }
        }
    }

    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "workspace"
                    url = uri(workspaceRepository)
                }
                providers.gradleProperty("enderfall.centralUrl").orNull?.let { releaseUrl ->
                    maven {
                        name = "centralStaging"
                        url = uri(releaseUrl)
                        credentials {
                            username = providers.environmentVariable("MAVEN_CENTRAL_USERNAME").orNull
                            password = providers.environmentVariable("MAVEN_CENTRAL_PASSWORD").orNull
                        }
                    }
                }
            }
        }
        val signingKey = providers.environmentVariable("MAVEN_SIGNING_KEY").orNull
        val signingPassword = providers.environmentVariable("MAVEN_SIGNING_PASSWORD").orNull
        if (!signingKey.isNullOrBlank()) {
            pluginManager.apply("signing")
            extensions.configure<SigningExtension> {
                useInMemoryPgpKeys(signingKey, signingPassword)
                sign(extensions.getByType<PublishingExtension>().publications)
            }
        }
    }
}

tasks.register("publishWorkspace") {
    group = "publishing"
    description = "Publishes SDK artifacts and plugin markers to build/repository for local template testing."
    dependsOn(":api:publishAllPublicationsToWorkspaceRepository")
    dependsOn(":bom:publishAllPublicationsToWorkspaceRepository")
    dependsOn(":gradle-plugin:publishAllPublicationsToWorkspaceRepository")
    dependsOn(":runtime-core:publishAllPublicationsToWorkspaceRepository")
}

tasks.register("buildAll") {
    group = "build"
    description = "Builds and verifies every EnderFall SDK module."
    dependsOn(subprojects.map { "${it.path}:build" })
}

tasks.register("checkAll") {
    group = "verification"
    description = "Runs every EnderFall SDK verification task."
    dependsOn(subprojects.map { "${it.path}:check" })
    dependsOn("verifyPinnedDependencies")
}

tasks.register("verifyPinnedDependencies") {
    group = "verification"
    description = "Rejects snapshots and dynamic dependency selectors."
    doLast {
        val violations = subprojects.flatMap { child ->
            child.configurations.flatMap { configuration ->
                configuration.dependencies.mapNotNull { dependency ->
                    val dependencyVersion = dependency.version ?: return@mapNotNull null
                    if (dependencyVersion.contains('+')
                        || dependencyVersion.equals("latest.release", ignoreCase = true)
                        || dependencyVersion.equals("latest.integration", ignoreCase = true)
                        || dependencyVersion.endsWith("-SNAPSHOT", ignoreCase = true)
                    ) {
                        "${child.path}:${configuration.name} -> ${dependency.group}:${dependency.name}:$dependencyVersion"
                    } else {
                        null
                    }
                }
            }
        }
        check(violations.isEmpty()) {
            "Unpinned dependencies are forbidden:\n${violations.joinToString("\n")}"
        }
    }
}

tasks.register<JavaExec>("checkApiCompatibility") {
    group = "verification"
    description = "Compares the API JAR with -Penderfall.previousApi=/path/to/previous.jar using japicmp."
    classpath = japicmp
    mainClass = "japicmp.JApiCmp"
    dependsOn(":api:jar")
    doFirst {
        val previous = providers.gradleProperty("enderfall.previousApi").orNull
            ?: error("Supply -Penderfall.previousApi=/path/to/previous-api.jar")
        val current = project(":api").tasks.named<Jar>("jar").get().archiveFile.get().asFile
        args = listOf(
            "--old", file(previous).absolutePath,
            "--new", current.absolutePath,
            "--only-modified",
            "--error-on-binary-incompatibility",
            "--error-on-source-incompatibility"
        )
    }
}

tasks.register("releaseChecksums") {
    group = "distribution"
    description = "Writes SHA-256 checksums for SDK JARs."
    dependsOn("buildAll")
    val output = layout.buildDirectory.file("checksums/SHA256SUMS")
    outputs.file(output)
    doLast {
        val jars = subprojects.flatMap { child ->
            child.layout.buildDirectory.dir("libs").get().asFileTree
                .matching { include("*.jar") }.files
        }.sortedBy { it.name }
        val digest = MessageDigest.getInstance("SHA-256")
        val lines = jars.map { jar ->
            digest.reset()
            val hash = digest.digest(jar.readBytes()).joinToString("") { "%02x".format(it) }
            "$hash  ${jar.name}"
        }
        val outputFile = output.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }
}

tasks.register("verifyRuntimeMatrix") {
    group = "verification"
    description = "Release gate for real target client/server and mixed-loader tests."
    doLast {
        throw GradleException(
            "Release blocked: target-native adapters and the real client/server mixed-loader matrix " +
                "have not been implemented and accepted yet. A successful Java build is not runtime proof."
        )
    }
}
