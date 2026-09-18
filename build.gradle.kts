import java.security.MessageDigest

plugins {
    base
    id("org.cyclonedx.bom") version "3.4.1"
}

val workspaceRepository = layout.buildDirectory.dir("repository")
val centralBundleRepository = layout.buildDirectory.dir("central-repository")
val verifyCentralBundleCredentials = tasks.register("verifyCentralBundleCredentials") {
    group = "publishing"
    description = "Fails before a Central bundle is assembled unless in-memory signing credentials are present."
    doLast {
        check(!providers.environmentVariable("MAVEN_SIGNING_KEY").orNull.isNullOrBlank()) {
            "MAVEN_SIGNING_KEY is required to create a Maven Central bundle."
        }
        check(!providers.environmentVariable("MAVEN_SIGNING_PASSWORD").orNull.isNullOrBlank()) {
            "MAVEN_SIGNING_PASSWORD is required to create a Maven Central bundle."
        }
    }
}
val cleanCentralBundleRepository = tasks.register<Delete>("cleanCentralBundleRepository") {
    group = "publishing"
    description = "Removes stale components before assembling a coordinated Central bundle."
    delete(centralBundleRepository)
}
tasks.register<JavaCompile>("compilePersistenceFixturePortableJava") {
    group = "development"
    description = "Compiles the unchanged persistence fixture against only the Java 17 portable API."
    sourceCompatibility = "17"
    targetCompatibility = "17"
    val apiCompile = project(":api").tasks.named<JavaCompile>("compileJava")
    source(layout.projectDirectory.dir("examples/persistent-preview/src/main/java"))
    classpath = files(apiCompile.flatMap { it.destinationDirectory })
    destinationDirectory.set(layout.buildDirectory.dir("classes/persistenceFixturePortable"))
    javaCompiler.set(project(":api").extensions.getByType<org.gradle.jvm.toolchain.JavaToolchainService>()
        .compilerFor { languageVersion.set(JavaLanguageVersion.of(17)) })
    options.release.set(17)
}
tasks.register("verifyPersistenceFixtures") {
    group = "verification"
    description = "Builds nine development fixtures and proves their portable class files are identical."
    if (providers.gradleProperty("enderfall.persistence").orNull == "true"
            && providers.gradleProperty("enderfall.persistenceFixture").orNull == "true") {
        val targets = listOf("fabric-1.20.1", "forge-1.20.1", "neoforge-1.20.1", "fabric-1.21.1",
            "neoforge-1.21.1", "fabric-1.21.4", "neoforge-1.21.4", "fabric-26.2", "neoforge-26.2")
        val archives = targets.map { project(":runtime-generated-$it").tasks.named<org.gradle.api.tasks.bundling.AbstractArchiveTask>("persistenceFixtureJar") }
        dependsOn(archives)
        val files = archives.map { it.flatMap { archive -> archive.archiveFile } }
        inputs.files(files)
        doLast {
            val reference = mutableMapOf<String, ByteArray>()
            files.forEach { file ->
                java.util.zip.ZipFile(file.get().asFile).use { zip ->
                    listOf("PersistentDemo", "PreviewBlocks", "PreviewConnectingTableBlock", "PreviewStorageCabinetBlock",
                        "PreviewContainers", "PreviewRecipes", "PreviewFluids", "PreviewGauge", "PreviewRenderClient").forEach { name ->
                        val path = "uk/co/enderfall/sdk/preview/$name.class"
                        val entry = zip.getEntry(path) ?: error("Missing portable class $path in ${file.get()}")
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        check(bytes.size > 8 && bytes[6].toInt() == 0 && bytes[7].toInt() == 61) { "$path must be Java 17 bytecode" }
                        val previous = reference.putIfAbsent(path, bytes)
                        check(previous == null || previous.contentEquals(bytes)) { "Portable class differs between targets: $path" }
                    }
                    val modernItems = file.get().asFile.name.contains("1.21.4-") || file.get().asFile.name.contains("26.2-")
                    val cabinetItem = "assets/enderfall_persistent_preview/" +
                        (if (modernItems) "items/storage_cabinet.json" else "models/item/storage_cabinet.json")
                    check(zip.getEntry(cabinetItem) != null) {
                        "Missing target-format preview asset $cabinetItem in ${file.get()}"
                    }
                    check(zip.getEntry("uk/co/enderfall/sdk/preview/GeneratedPersistenceEntrypoint.class") != null)
                    logger.lifecycle("Portable fixture parity verified: ${file.get().asFile.name}")
                }
            }
        }
    } else {
        doLast { error("Use '-Penderfall.persistence=true' and '-Penderfall.persistenceFixture=true'") }
    }
}
tasks.register("verifyPersistenceArtifacts") {
    group = "verification"
    description = "Checks complete feature contents in all nine development runtime JARs."
    if (providers.gradleProperty("enderfall.persistence").orNull == "true") {
        val targets = listOf("fabric-1.20.1", "fabric-1.21.1", "fabric-1.21.4", "fabric-26.2",
            "forge-1.20.1", "neoforge-1.20.1", "neoforge-1.21.1", "neoforge-1.21.4", "neoforge-26.2")
        val archives = targets.map { target ->
            val task = when {
                target == "fabric-26.2" -> "jar"
                target.startsWith("fabric-") -> "remapJar"
                target.endsWith("1.20.1") -> "reobfShadowJar"
                else -> "shadowJar"
            }
            project(":runtime-generated-$target").tasks.named<org.gradle.api.tasks.bundling.AbstractArchiveTask>(task)
        }
        dependsOn(archives)
        val files = archives.map { it.flatMap { archive -> archive.archiveFile } }
        inputs.files(files)
        doLast {
            files.forEach { file ->
                java.util.zip.ZipFile(file.get().asFile).use { zip ->
                    val entries = zip.entries().asSequence().toList()
                    check(entries.count { it.name == "uk/co/enderfall/sdk/runtime/blockentity/nativebridge/StoredBlockEntity.class" } == 1) { "Missing storage in ${file.get()}" }
                    check(entries.count { it.name.endsWith("TimedWorkbenchProcessor.class") } == 1) { "Missing processor in ${file.get()}" }
                    val bootstrap = entries.single { it.name.endsWith("ConsumerBootstrap.class") }
                    val constants = zip.getInputStream(bootstrap).use { String(it.readBytes(), Charsets.ISO_8859_1) }
                    check(constants.contains("PersistentPlatformAdapter")) { "Baseline bootstrap packaged in ${file.get()}" }
                    logger.lifecycle("Persistence artifact verified: ${file.get().asFile.name}")
                }
            }
        }
    } else {
        doLast { error("Use '-Penderfall.persistence=true' for development persistence artifact verification") }
    }
}
val generatedBridgeWorkspaceRepository = layout.buildDirectory.dir("generated-bridge-repository")
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
        val referenceRuntime = project.name.startsWith("runtime-") && project.name != "runtime-core"
            && !project.name.startsWith("runtime-generated-")
        if (referenceRuntime) {
            // Retained for parity only: never overwrite the generated runtime coordinates.
            tasks.withType<org.gradle.api.publish.maven.tasks.AbstractPublishToMaven>().configureEach {
                enabled = false
            }
        }
        if (project.name.startsWith("runtime-generated-")) {
            if (providers.gradleProperty("enderfall.persistence").orNull == "true") {
                tasks.named<JavaCompile>("compileJava") {
                    destinationDirectory.set(layout.buildDirectory.dir("classes/persistenceJava/main"))
                }
                tasks.named<ProcessResources>("processResources") {
                    destinationDir = layout.buildDirectory.dir("resources/persistenceMain").get().asFile
                }
                extensions.getByType<SourceSetContainer>().named("main") {
                    (output.classesDirs as ConfigurableFileCollection).setFrom(tasks.named<JavaCompile>("compileJava").flatMap { it.destinationDirectory })
                    output.setResourcesDir(layout.buildDirectory.dir("resources/persistenceMain").get().asFile)
                }
                tasks.withType<org.gradle.api.publish.maven.tasks.AbstractPublishToMaven>().configureEach {
                    enabled = false
                }
                tasks.withType<org.gradle.api.tasks.bundling.AbstractArchiveTask>().configureEach {
                    archiveAppendix.set("persistence-dev")
                }
            }
            tasks.withType<org.gradle.api.publish.maven.tasks.AbstractPublishToMaven>().configureEach {
                dependsOn(rootProject.tasks.named("verifyBridgeCoverage"))
                if (providers.gradleProperty("enderfall.referenceRuntimes").orNull == "false") {
                    doFirst { error("Publishing requires reference parity until the reference-runtime retirement gate is complete.") }
                }
            }
        }
        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                pom {
                    name.convention(project.name)
                    description.convention(project.description ?: project.name)
                    url.set("https://github.com/EnderFall-LTD/enderfall-sdk")
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
                        url.set("https://github.com/EnderFall-LTD/enderfall-sdk")
                        connection.set("scm:git:https://github.com/EnderFall-LTD/enderfall-sdk.git")
                        developerConnection.set("scm:git:ssh://git@github.com/EnderFall-LTD/enderfall-sdk.git")
                    }
                }
            }
            repositories {
                if (!referenceRuntime) {
                    maven {
                        name = "workspace"
                        url = uri(workspaceRepository)
                    }
                    maven {
                        name = "generatedBridgeWorkspace"
                        url = uri(generatedBridgeWorkspaceRepository)
                    }
                    maven {
                        name = "centralBundle"
                        url = uri(centralBundleRepository)
                    }
                }
            }
        }
        val signingKey = providers.environmentVariable("MAVEN_SIGNING_KEY").orNull
        val signingPassword = providers.environmentVariable("MAVEN_SIGNING_PASSWORD").orNull
        if (!referenceRuntime && !signingKey.isNullOrBlank()) {
            pluginManager.apply("signing")
            extensions.configure<SigningExtension> {
                useInMemoryPgpKeys(signingKey, signingPassword)
                sign(extensions.getByType<PublishingExtension>().publications)
            }
        }
        tasks.withType<org.gradle.api.publish.maven.tasks.PublishToMavenRepository>()
            .matching { it.name.endsWith("ToCentralBundleRepository") }
            .configureEach {
                dependsOn(verifyCentralBundleCredentials)
                dependsOn(cleanCentralBundleRepository)
            }
    }
}

tasks.register("publishWorkspace") {
    group = "publishing"
    description = "Publishes generated SDK artifacts and plugin markers to build/repository for local template testing."
    dependsOn(":api:publishAllPublicationsToWorkspaceRepository")
    dependsOn(":bom:publishAllPublicationsToWorkspaceRepository")
    dependsOn(":gradle-plugin:publishAllPublicationsToWorkspaceRepository")
    dependsOn(":runtime-core:publishAllPublicationsToWorkspaceRepository")
    dependsOn(subprojects.filter { it.name.startsWith("runtime-generated-") }
        .map { "${it.path}:publishAllPublicationsToWorkspaceRepository" })
}

tasks.register("publishGeneratedBridgeWorkspace") {
    group = "publishing"
    description = "Publishes common SDK artifacts and generated runtimes to an isolated smoke-test repository."
    dependsOn(":api:publishAllPublicationsToGeneratedBridgeWorkspaceRepository")
    dependsOn(":bom:publishAllPublicationsToGeneratedBridgeWorkspaceRepository")
    dependsOn(":gradle-plugin:publishAllPublicationsToGeneratedBridgeWorkspaceRepository")
    dependsOn(":runtime-core:publishAllPublicationsToGeneratedBridgeWorkspaceRepository")
    dependsOn(subprojects.filter { it.name.startsWith("runtime-generated-") }
        .map { "${it.path}:publishAllPublicationsToGeneratedBridgeWorkspaceRepository" })
}

val publishCentralBundleRepository = tasks.register("publishCentralBundleRepository") {
    group = "publishing"
    description = "Publishes all coordinated, signed SDK components into an isolated Central bundle repository."
    dependsOn(":api:publishAllPublicationsToCentralBundleRepository")
    dependsOn(":bom:publishAllPublicationsToCentralBundleRepository")
    dependsOn(":gradle-plugin:publishAllPublicationsToCentralBundleRepository")
    dependsOn(":runtime-core:publishAllPublicationsToCentralBundleRepository")
    dependsOn(subprojects.filter { it.name.startsWith("runtime-generated-") }
        .map { "${it.path}:publishAllPublicationsToCentralBundleRepository" })
}

val verifyCentralBundleRepository = tasks.register("verifyCentralBundleRepository") {
    group = "publishing"
    description = "Checks coordinated Central components, signatures, and required checksums before archiving."
    dependsOn(publishCentralBundleRepository)
    inputs.dir(centralBundleRepository)
    doLast {
        val repositoryRoot = centralBundleRepository.get().asFile
        val groupRoot = repositoryRoot.resolve("uk/co/enderfall/sdk")
        check(groupRoot.isDirectory) { "Missing Maven group in Central bundle repository: $groupRoot" }
        val expectedComponents = setOf(
            "enderfall-sdk-api",
            "enderfall-sdk-bom",
            "enderfall-sdk-gradle-plugin",
            "enderfall-sdk-runtime-core",
            "enderfall-sdk-runtime-1.20.1-fabric",
            "enderfall-sdk-runtime-1.20.1-forge",
            "enderfall-sdk-runtime-1.20.1-neoforge",
            "enderfall-sdk-runtime-1.21.1-fabric",
            "enderfall-sdk-runtime-1.21.1-neoforge",
            "enderfall-sdk-runtime-1.21.4-fabric",
            "enderfall-sdk-runtime-1.21.4-neoforge",
            "enderfall-sdk-runtime-26.2-fabric",
            "enderfall-sdk-runtime-26.2-neoforge",
            "uk.co.enderfall.sdk.gradle.plugin"
        )
        val actualComponents = groupRoot.listFiles().orEmpty()
            .filter(File::isDirectory)
            .map(File::getName)
            .toSet()
        check(actualComponents == expectedComponents) {
            "Central bundle component mismatch. Expected $expectedComponents but found $actualComponents"
        }
        val publicationFiles = groupRoot.walkTopDown()
            .filter(File::isFile)
            .filterNot { it.name.startsWith("maven-metadata.xml") }
            .filterNot { it.extension in setOf("asc", "md5", "sha1", "sha256", "sha512") }
            .toList()
        check(publicationFiles.isNotEmpty()) { "Central bundle contains no publication files" }
        publicationFiles.forEach { published ->
            listOf("asc", "md5", "sha1").forEach { extension ->
                check(File(published.parentFile, "${published.name}.$extension").isFile) {
                    "Missing .$extension companion for ${published.relativeTo(repositoryRoot)}"
                }
            }
        }
    }
}

tasks.register<Zip>("centralBundle") {
    group = "publishing"
    description = "Creates the single signed Maven repository archive uploaded to the Central Publisher API."
    dependsOn(verifyCentralBundleRepository)
    from(centralBundleRepository)
    exclude("**/maven-metadata.xml", "**/maven-metadata.xml.*")
    destinationDirectory.set(layout.buildDirectory.dir("central"))
    archiveFileName.set("enderfall-sdk-${project.version}-central.zip")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
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

tasks.register("generateAllBridges") {
    group = "build"
    description = "Generates every runtime target currently implemented by the bridge compiler."
    dependsOn(subprojects.filter { it.name.startsWith("runtime-generated-") }
        .map { "${it.path}:generateBridgeRuntime" })
}

tasks.register("compileGeneratedRuntimes") {
    group = "build"
    description = "Compiles all generated runtimes without invoking reference parity checks."
    dependsOn(subprojects.filter { it.name.startsWith("runtime-generated-") }
        .map { "${it.path}:classes" })
}

tasks.register("verifyBridgeCoverage") {
    group = "verification"
    description = "Validates the bridge ABI catalog and generated runtime parity targets."
    dependsOn(":bridge-compiler:test")
    dependsOn(subprojects.filter { it.name.startsWith("runtime-generated-") }
        .map { "${it.path}:check" })
}

tasks.named("checkAll") {
    dependsOn("verifyBridgeCoverage")
}

tasks.register<GradleBuild>("checkContractTargets") {
    group = "verification"
    description = "Builds the full portable contract mod unchanged for all nine targets."
    dependsOn("publishWorkspace")
    dir = file("test-mod")
    tasks = listOf("buildAll")
}

tasks.register<GradleBuild>("checkDemoMod") {
    group = "verification"
    description = "Builds the structured EnderFall SDK demo unchanged for all nine targets."
    dependsOn("publishWorkspace")
    // Concurrent nested builds share the embedded Kotlin compiler application.
    mustRunAfter("checkContractTargets")
    dir = file("examples/demo-mod")
    tasks = listOf("buildAll")
}

tasks.register("serverSmoke") {
    group = "verification"
    description = "Starts and cleanly stops the contract-test dedicated server on every selected target."
    dependsOn(":integration-harness:serverSmoke")
}

tasks.register("clientSmoke") {
    group = "verification"
    description = "Starts the contract-test client and verifies lifecycle/tick readiness on selected targets."
    dependsOn(":integration-harness:clientSmoke")
}

tasks.register("generatedBridgeServerSmoke") {
    group = "verification"
    description = "Starts contract-test servers using only centrally generated runtime artifacts."
    dependsOn(":integration-harness:generatedBridgeServerSmoke")
}

tasks.register("generatedBridgeClientSmoke") {
    group = "verification"
    description = "Starts contract-test clients using only centrally generated runtime artifacts."
    dependsOn(":integration-harness:generatedBridgeClientSmoke")
}

tasks.register("sameLoaderSmoke") {
    group = "verification"
    description = "Connects real clients to dedicated servers using the same loader and Minecraft version."
    dependsOn(":integration-harness:sameLoaderSmoke")
}

tasks.register("generatedBridgeGameplaySmoke") {
    group = "verification"
    description = "Runs connected menu/workbench gameplay assertions against centrally generated runtimes."
    dependsOn(":integration-harness:generatedBridgeGameplaySmoke")
}

tasks.register("verifyGameplayMatrix") {
    group = "verification"
    description = "Audits ordered gameplay reports and logs for complete nine-target evidence."
    dependsOn(":integration-harness:verifyGameplayMatrix")
}

val unpinnedDependencies = objects.listProperty<String>()
subprojects.forEach { child ->
    child.configurations.configureEach {
        val configurationName = name
        dependencies.configureEach {
            val dependencyVersion = version ?: return@configureEach
            if (dependencyVersion.endsWith("+")
                || dependencyVersion.contains('*')
                || dependencyVersion.startsWith('[')
                || dependencyVersion.startsWith('(')
                || dependencyVersion.equals("latest.release", ignoreCase = true)
                || dependencyVersion.equals("latest.integration", ignoreCase = true)
                || dependencyVersion.endsWith("-SNAPSHOT", ignoreCase = true)
            ) {
                unpinnedDependencies.add(
                    "${child.path}:$configurationName -> $group:$name:$dependencyVersion"
                )
            }
        }
    }
}

tasks.register("verifyPinnedDependencies") {
    group = "verification"
    description = "Rejects snapshots and dynamic dependency selectors."
    inputs.property("unpinnedDependencies", unpinnedDependencies)
    doLast {
        @Suppress("UNCHECKED_CAST")
        val violations = inputs.properties["unpinnedDependencies"] as List<String>
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
    dependsOn(":runtime-generated-forge-1.20.1:reobfShadowJar")
    dependsOn(":runtime-generated-neoforge-1.20.1:reobfShadowJar")
    val output = layout.buildDirectory.file("checksums/SHA256SUMS")
    val releaseJars = provider {
        subprojects.filter {
            it.name != "test-mod" &&
                it.name != "bridge-compiler" &&
                (!it.name.startsWith("runtime-") || it.name == "runtime-core" ||
                    it.name.startsWith("runtime-generated-"))
        }.flatMap { child ->
            child.layout.buildDirectory.dir("libs").get().asFileTree
                .matching {
                    include("*.jar")
                    exclude("*-plain.jar")
                    exclude("*persistence-dev*.jar")
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
    description = "Release gate for complete real-target foundation-feature acceptance."
    doLast {
        throw GradleException(
            "Release blocked: all target-native adapters compile, lifecycle smoke tests pass, and the " +
                "exact same-loader connection matrix completes its SDK packet round trip, but the complete " +
                "in-game foundation-feature matrix has not been accepted yet. Those results are separate " +
                "from build, launch, and network-smoke proof."
        )
    }
}
