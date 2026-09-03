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

allprojects {
    // Build the SBOM from Gradle's verified resolution graph. Repository POM
    // enrichment performs a second, unbounded metadata resolution pass and is
    // not needed for a complete component/dependency inventory.
    tasks.withType<org.cyclonedx.gradle.CyclonedxDirectTask>().configureEach {
        includeMetadataResolution.set(false)
    }
}

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
            publications.withType<MavenPublication>().configureEach {
                pom {
                    name.convention(project.name)
                    description.convention(project.description ?: project.name)
                    url.set("https://github.com/EnderFall/enderfall-sdk")
                    licenses {
                        license {
                            name.set("Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("enderfall")
                            name.set("EnderFall")
                        }
                    }
                    scm {
                        url.set("https://github.com/EnderFall/enderfall-sdk")
                        connection.set("scm:git:https://github.com/EnderFall/enderfall-sdk.git")
                        developerConnection.set("scm:git:ssh://git@github.com/EnderFall/enderfall-sdk.git")
                    }
                }
            }
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
    dependsOn(":runtime-fabric-1.20.1:publishAllPublicationsToWorkspaceRepository")
    dependsOn(":runtime-fabric-1.21.1:publishAllPublicationsToWorkspaceRepository")
    dependsOn(":runtime-fabric-1.21.4:publishAllPublicationsToWorkspaceRepository")
    dependsOn(":runtime-fabric-26.2:publishAllPublicationsToWorkspaceRepository")
    dependsOn(":runtime-forge-1.20.1:publishAllPublicationsToWorkspaceRepository")
    dependsOn(":runtime-neoforge-1.20.1:publishAllPublicationsToWorkspaceRepository")
    dependsOn(":runtime-neoforge-1.21.4:publishAllPublicationsToWorkspaceRepository")
    dependsOn(":runtime-neoforge-1.21.1:publishAllPublicationsToWorkspaceRepository")
    dependsOn(":runtime-neoforge-26.2:publishAllPublicationsToWorkspaceRepository")
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

tasks.register<GradleBuild>("checkContractTargets") {
    group = "verification"
    description = "Builds the full portable contract mod unchanged for all nine targets."
    dependsOn("publishWorkspace")
    dir = file("test-mod")
    tasks = listOf("buildAll")
}

tasks.register("verifyPinnedDependencies") {
    group = "verification"
    description = "Rejects snapshots and dynamic dependency selectors."
    doLast {
        val violations = subprojects.flatMap { child ->
            child.configurations.flatMap { configuration ->
                configuration.dependencies.mapNotNull { dependency ->
                    val dependencyVersion = dependency.version ?: return@mapNotNull null
                    if (dependencyVersion.endsWith("+")
                        || dependencyVersion.contains('*')
                        || dependencyVersion.startsWith('[')
                        || dependencyVersion.startsWith('(')
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
    dependsOn(":runtime-forge-1.20.1:reobfShadowJar")
    dependsOn(":runtime-neoforge-1.20.1:reobfShadowJar")
    val output = layout.buildDirectory.file("checksums/SHA256SUMS")
    val releaseJars = provider {
        subprojects.filter { it.name != "test-mod" }.flatMap { child ->
            child.layout.buildDirectory.dir("libs").get().asFileTree
                .matching {
                    include("*.jar")
                    exclude("*-plain.jar")
                }.files
        }.sortedBy { it.name }
    }
    inputs.files(releaseJars)
        .withPropertyName("releaseJars")
        .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    outputs.file(output)
    doLast {
        val digest = MessageDigest.getInstance("SHA-256")
        val lines = releaseJars.get().map { jar ->
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
            "Release blocked: all target-native adapters compile and dedicated-server startup has been " +
                "smoke-tested, but the automated client and same-version mixed-loader matrix has not " +
                "been implemented and accepted yet. A successful Java build or server launch is not " +
                "mixed-loader runtime proof."
        )
    }
}
