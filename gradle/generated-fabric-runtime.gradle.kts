import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.process.CommandLineArgumentProvider
import java.io.File
import java.nio.file.Files

private enum class GeneratedFabricReferenceLayout {
    DIRECT,
    INHERITED_1_21_4,
}

private data class GeneratedFabricTargetSpec(
    val id: String,
    val minecraftVersion: String,
    val javaVersion: Int,
    val loader: String,
    val fabricLoaderVersion: String,
    val fabricApiVersion: String,
    val referenceProjectPath: String,
    val referenceLayout: GeneratedFabricReferenceLayout,
    val useOfficialMojangMappings: Boolean = true,
    val inheritedReferenceJavaFiles: List<String> = emptyList(),
    val localReferenceJavaFiles: List<String> = emptyList(),
    val runtimeArtifactTaskName: String = "remapJar",
    val loaderDependencyConfiguration: String = "modImplementation",
    val normalizeArtifactParity: Boolean = false,
    val suppressAuxiliaryClassWarning: Boolean = false,
)

private val inherited1214JavaFilesFor1211 = listOf(
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/EnderfallFabricRuntime.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricClientHooks.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricCommandBridge.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricConsumerBootstrap.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricPlatformInfo.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricPortableMenuScreen.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricRawPayload.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricRecipeBinding.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricWorkbenchBinding.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricWorkbenchInput.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricWorkbenchScreen.java",
)

private val local1211JavaFiles = listOf(
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/Fabric1211PlatformAdapter.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/Fabric1211WorkbenchMenu.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/Fabric1211WorkbenchRecipe.java",
)

private val inherited1214JavaFilesFor262 = listOf(
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/EnderfallFabricRuntime.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricClientHooks.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricConsumerBootstrap.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricPlatformInfo.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricRawPayload.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricRecipeBinding.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricWorkbenchBinding.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricWorkbenchInput.java",
)

private val local262JavaFiles = listOf(
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/Fabric26CommandBridge.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/Fabric26PlatformAdapter.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/Fabric26PortableMenuScreen.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/Fabric26WorkbenchMenu.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/Fabric26WorkbenchRecipe.java",
    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/Fabric26WorkbenchScreen.java",
)

private val generatedFabricTargets = listOf(
    GeneratedFabricTargetSpec(
        id = "1.20.1-fabric",
        minecraftVersion = "1.20.1",
        javaVersion = 17,
        loader = "fabric",
        fabricLoaderVersion = "0.19.5",
        fabricApiVersion = "0.92.12+1.20.1",
        referenceProjectPath = ":runtime-fabric-1.20.1",
        referenceLayout = GeneratedFabricReferenceLayout.INHERITED_1_21_4,
        inheritedReferenceJavaFiles = listOf("EnderfallFabricRuntime", "FabricCommandBridge",
            "FabricConsumerBootstrap", "FabricPlatformInfo")
            .map { "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/$it.java" },
        localReferenceJavaFiles = listOf("ClientHooks", "PlatformAdapter", "WorkbenchBinding",
            "WorkbenchMenu", "WorkbenchRecipe", "WorkbenchScreen")
            .map { "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/Fabric1201$it.java" } +
            "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricPortableMenuScreen.java",
        suppressAuxiliaryClassWarning = true,
    ),
    GeneratedFabricTargetSpec(
        id = "1.21.4-fabric",
        minecraftVersion = "1.21.4",
        javaVersion = 21,
        loader = "fabric",
        fabricLoaderVersion = "0.19.5",
        fabricApiVersion = "0.119.4+1.21.4",
        referenceProjectPath = ":runtime-fabric-1.21.4",
        referenceLayout = GeneratedFabricReferenceLayout.DIRECT,
    ),
    GeneratedFabricTargetSpec(
        id = "1.21.1-fabric",
        minecraftVersion = "1.21.1",
        javaVersion = 21,
        loader = "fabric",
        fabricLoaderVersion = "0.19.5",
        fabricApiVersion = "0.116.17+1.21.1",
        referenceProjectPath = ":runtime-fabric-1.21.1",
        referenceLayout = GeneratedFabricReferenceLayout.INHERITED_1_21_4,
        inheritedReferenceJavaFiles = inherited1214JavaFilesFor1211,
        localReferenceJavaFiles = local1211JavaFiles,
        normalizeArtifactParity = true,
        suppressAuxiliaryClassWarning = true,
    ),
    GeneratedFabricTargetSpec(
        id = "26.2-fabric",
        minecraftVersion = "26.2",
        javaVersion = 25,
        loader = "fabric",
        fabricLoaderVersion = "0.19.5",
        fabricApiVersion = "0.159.0+26.2",
        referenceProjectPath = ":runtime-fabric-26.2",
        referenceLayout = GeneratedFabricReferenceLayout.INHERITED_1_21_4,
        useOfficialMojangMappings = false,
        inheritedReferenceJavaFiles = inherited1214JavaFilesFor262,
        localReferenceJavaFiles = local262JavaFiles,
        runtimeArtifactTaskName = "jar",
        loaderDependencyConfiguration = "implementation",
        suppressAuxiliaryClassWarning = true,
    ),
).associateBy(GeneratedFabricTargetSpec::id)

abstract class GeneratedBridgePathExistenceParameters : ValueSourceParameters {
    abstract val path: Property<String>
}

abstract class GeneratedBridgePathExistenceValueSource :
    ValueSource<String, GeneratedBridgePathExistenceParameters> {
    override fun obtain(): String {
        val path = File(parameters.path.get()).toPath().toAbsolutePath().normalize()
        return "$path|${Files.exists(path)}"
    }
}

abstract class GeneratedBridgeGenerationArguments : CommandLineArgumentProvider {
    @get:Input
    abstract val targetId: Property<String>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val canonicalRoot: DirectoryProperty

    @get:Internal
    abstract val outputSources: DirectoryProperty

    @get:Internal
    abstract val outputResources: DirectoryProperty

    override fun asArguments(): Iterable<String> = listOf(
        "--target", targetId.get(),
        "--canonical-root", canonicalRoot.path(),
        "--output-sources", outputSources.path(),
        "--output-resources", outputResources.path(),
    )

    private fun DirectoryProperty.path(): String = get().asFile.absolutePath
}

abstract class GeneratedBridgeDirectParityArguments : CommandLineArgumentProvider {
    @get:Internal
    abstract val forbiddenJava: DirectoryProperty

    @get:Input
    abstract val forbiddenJavaState: Property<String>

    @get:Internal
    abstract val forbiddenResources: DirectoryProperty

    @get:Input
    abstract val forbiddenResourcesState: Property<String>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val referenceJava: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val referenceResources: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val canonicalJava: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val canonicalResources: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedJava: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedResources: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val referenceClasses: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedClasses: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val referenceProcessedResources: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedProcessedResources: DirectoryProperty

    override fun asArguments(): Iterable<String> = listOf(
        "--forbidden-root", forbiddenJava.path(),
        "--forbidden-root", forbiddenResources.path(),
        "--expected", referenceJava.path(),
        "--actual", canonicalJava.path(),
        "--expected", referenceResources.path(),
        "--actual", canonicalResources.path(),
        "--expected", canonicalJava.path(),
        "--actual", generatedJava.path(),
        "--expected", canonicalResources.path(),
        "--actual", generatedResources.path(),
        "--expected", referenceClasses.path(),
        "--actual", generatedClasses.path(),
        "--expected", referenceProcessedResources.path(),
        "--actual", generatedProcessedResources.path(),
    )

    private fun DirectoryProperty.path(): String = get().asFile.absolutePath
}

abstract class GeneratedBridgeInheritedParityArguments : CommandLineArgumentProvider {
    @get:Internal
    abstract val forbiddenJava: DirectoryProperty

    @get:Input
    abstract val forbiddenJavaState: Property<String>

    @get:Internal
    abstract val forbiddenResources: DirectoryProperty

    @get:Input
    abstract val forbiddenResourcesState: Property<String>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val effectiveReferenceJava: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedJava: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val referenceClasses: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedClasses: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val referenceSourcePackMetadata: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val generatedSourcePackMetadata: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val referenceProcessedPackMetadata: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val generatedProcessedPackMetadata: RegularFileProperty

    override fun asArguments(): Iterable<String> = listOf(
        "--forbidden-root", forbiddenJava.path(),
        "--forbidden-root", forbiddenResources.path(),
        "--expected", effectiveReferenceJava.path(),
        "--actual", generatedJava.path(),
        "--expected", referenceClasses.path(),
        "--actual", generatedClasses.path(),
        "--expected-file", referenceSourcePackMetadata.path(),
        "--actual-file", generatedSourcePackMetadata.path(),
        "--expected-file", referenceProcessedPackMetadata.path(),
        "--actual-file", generatedProcessedPackMetadata.path(),
    )

    private fun DirectoryProperty.path(): String = get().asFile.absolutePath
    private fun RegularFileProperty.path(): String = get().asFile.absolutePath
}

abstract class GeneratedBridgeArtifactParityArguments : CommandLineArgumentProvider {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val referenceRuntimeJar: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val generatedRuntimeJar: RegularFileProperty

    override fun asArguments(): Iterable<String> = listOf(
        "--expected-file", referenceRuntimeJar.get().asFile.absolutePath,
        "--actual-file", generatedRuntimeJar.get().asFile.absolutePath,
    )
}

abstract class GeneratedBridgeNormalizedArtifactParityArguments : CommandLineArgumentProvider {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val referenceEntries: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatedEntries: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val referencePackMetadata: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val generatedPackMetadata: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val intentionallyDifferentReferenceMetadata: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val intentionallyDifferentGeneratedMetadata: RegularFileProperty

    override fun asArguments(): Iterable<String> = listOf(
        "--expected", referenceEntries.get().asFile.absolutePath,
        "--actual", generatedEntries.get().asFile.absolutePath,
        "--expected-file", referencePackMetadata.get().asFile.absolutePath,
        "--actual-file", generatedPackMetadata.get().asFile.absolutePath,
    )
}

abstract class GeneratedBridgeTargetCoordinateArguments : CommandLineArgumentProvider {
    @get:Input
    abstract val targetId: Property<String>

    @get:Input
    abstract val minecraftVersion: Property<String>

    @get:Input
    abstract val javaVersion: Property<Int>

    @get:Input
    abstract val loader: Property<String>

    @get:Input
    abstract val loaderVersion: Property<String>

    @get:Input
    abstract val platformApiVersion: Property<String>

    override fun asArguments(): Iterable<String> = listOf(
        "--target", targetId.get(),
        "--expect", "minecraftVersion=${minecraftVersion.get()}",
        "--expect", "javaVersion=${javaVersion.get()}",
        "--expect", "loader=${loader.get()}",
        "--expect", "loaderVersion=${loaderVersion.get()}",
        "--expect", "platformApiVersion=${platformApiVersion.get()}",
    )
}

val generatedFabricTargetId = extensions.extraProperties.properties["enderfallGeneratedFabricTarget"] as? String
    ?: throw GradleException(
        "Set extra[\"enderfallGeneratedFabricTarget\"] before applying generated-fabric-runtime.gradle.kts"
    )
private val generatedFabricTarget = generatedFabricTargets[generatedFabricTargetId]
    ?: throw GradleException(
        "Unsupported generated Fabric target '$generatedFabricTargetId'. Valid targets: " +
            generatedFabricTargets.keys.sorted().joinToString()
    )

description = "Generated EnderFall SDK runtime parity target for Minecraft " +
    "${generatedFabricTarget.minecraftVersion} on Fabric"

pluginManager.apply("maven-publish")
extensions.configure<org.gradle.api.publish.PublishingExtension> {
    publications {
        create<org.gradle.api.publish.maven.MavenPublication>("mavenJava") {
            artifactId = "enderfall-sdk-runtime-${generatedFabricTarget.minecraftVersion}-fabric"
            from(components["java"])
        }
    }
}

extensions.configure<JavaPluginExtension> {
    toolchain.languageVersion = JavaLanguageVersion.of(generatedFabricTarget.javaVersion)
    withSourcesJar()
    withJavadocJar()
}

val canonicalBridgeRoot = rootProject.layout.projectDirectory.dir("bridge-runtime")
val bridgeProfile = if (providers.gradleProperty("enderfall.persistence").orNull == "true") "enderfallPersistenceWorldState2" else "enderfallBridge"
val generatedBridgeSources = layout.buildDirectory.dir("generated/sources/$bridgeProfile")
val generatedBridgeResources = layout.buildDirectory.dir("generated/resources/$bridgeProfile")

val bridgeCompilerRuntime = configurations.create("bridgeCompilerRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val loomExtension = extensions.getByName("loom")
dependencies.add("minecraft", "com.mojang:minecraft:${generatedFabricTarget.minecraftVersion}")
// Minecraft exposes Guava collections; retain strict classfile checks without bundling annotations.
dependencies.add("compileOnly", "com.google.errorprone:error_prone_annotations:2.36.0")
if (generatedFabricTarget.useOfficialMojangMappings) {
    val officialMojangMappings = requireNotNull(
        loomExtension.javaClass.methods.singleOrNull {
            it.name == "officialMojangMappings" && it.parameterCount == 0
        }?.invoke(loomExtension)
    ) { "Fabric Loom does not expose officialMojangMappings()" }
    dependencies.add("mappings", officialMojangMappings)
}
dependencies.add(
    generatedFabricTarget.loaderDependencyConfiguration,
    "net.fabricmc:fabric-loader:${generatedFabricTarget.fabricLoaderVersion}"
)
dependencies.add(
    generatedFabricTarget.loaderDependencyConfiguration,
    "net.fabricmc.fabric-api:fabric-api:${generatedFabricTarget.fabricApiVersion}"
)
dependencies.add(
    "compileOnly",
    dependencies.project(mapOf("path" to ":runtime-core"))
)
dependencies.add(
    "include",
    dependencies.project(mapOf("path" to ":runtime-core", "configuration" to "shadowRuntimeElements"))
)
// Loom's include packages the runtime for distribution, but does not put it on
// the development launch classpath. Use the same relocated artifact in dev.
dependencies.add(
    "runtimeOnly",
    dependencies.project(mapOf("path" to ":runtime-core", "configuration" to "shadowRuntimeElements"))
)
dependencies.add(
    bridgeCompilerRuntime.name,
    dependencies.project(mapOf("path" to ":bridge-compiler"))
)

val javaToolchainService = extensions.getByType<JavaToolchainService>()

// SDK native-feature port lanes; these do not enable incomplete target capabilities for consumers.
if (generatedFabricTarget.id in setOf("1.20.1-fabric", "1.21.1-fabric", "1.21.4-fabric", "26.2-fabric")) {
    val storageSources = layout.buildDirectory.dir("generated/blockEntityStorage/java")
    val storageCompileClasspath = extensions.getByType<SourceSetContainer>().named("main").get().compileClasspath
    val generateStorage = tasks.register<JavaExec>("generateBlockEntityStorage") {
        group = "development"
        description = "Generates shared native block-entity storage for the reviewed target ABI."
        classpath = bridgeCompilerRuntime
        mainClass.set("uk.co.enderfall.sdk.bridge.BlockEntityStorageMain")
        javaLauncher.set(javaToolchainService.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
        inputs.property("target", generatedFabricTarget.id)
        outputs.dir(storageSources)
        args(generatedFabricTarget.id, storageSources.get().asFile.absolutePath)
    }
    tasks.register<JavaCompile>("compileBlockEntityStorageJava") {
        group = "development"
        description = "Checks native storage compatibility without publishing or enabling incomplete platform support."
        dependsOn(generateStorage)
        source(storageSources)
        classpath = storageCompileClasspath
        destinationDirectory.set(layout.buildDirectory.dir("classes/blockEntityStorage"))
        javaCompiler.set(javaToolchainService.compilerFor { languageVersion.set(JavaLanguageVersion.of(generatedFabricTarget.javaVersion)) })
        options.release.set(generatedFabricTarget.javaVersion)
    }
}

// Compile unfinished native features without changing shipped artifacts or historical parity fixtures.
if (generatedFabricTarget.id in setOf("1.20.1-fabric", "1.21.1-fabric", "1.21.4-fabric", "26.2-fabric")) {
    val previewSources = layout.buildDirectory.dir("generated/blockEntityPreview/java")
    val generatePreview = tasks.register<JavaExec>("generateBlockEntityPreview") {
        group = "development"
        description = "Generates the isolated native block-entity persistence development slice."
        classpath = bridgeCompilerRuntime
        mainClass.set("uk.co.enderfall.sdk.bridge.BlockEntityPreviewMain")
        javaLauncher.set(javaToolchainService.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
        inputs.property("target", generatedFabricTarget.id)
        outputs.dir(previewSources)
        args(generatedFabricTarget.id, previewSources.get().asFile.absolutePath)
    }
    val mainSourceSet = extensions.getByType<SourceSetContainer>().named("main")
    val compilePreview = tasks.register<JavaCompile>("compileBlockEntityPreviewJava") {
        group = "development"
        description = "Compiles experimental block-entity hooks against pinned Minecraft/Fabric APIs; does not package them."
        dependsOn(generatePreview)
        source(previewSources)
        classpath = mainSourceSet.get().compileClasspath + mainSourceSet.get().output
        destinationDirectory.set(layout.buildDirectory.dir("classes/blockEntityPreview"))
        javaCompiler.set(javaToolchainService.compilerFor { languageVersion.set(JavaLanguageVersion.of(generatedFabricTarget.javaVersion)) })
        options.release.set(generatedFabricTarget.javaVersion)
    }
    if (generatedFabricTarget.id == "1.21.4-fabric") {
    val fixtureRoot = rootProject.layout.projectDirectory.dir("examples/persistent-preview")
    val portableApi = configurations.create("persistentPreviewPortableApi") {
        isCanBeResolved = true
        isCanBeConsumed = false
    }
    dependencies.add(portableApi.name, dependencies.project(mapOf("path" to ":api", "configuration" to "apiElements")))
    val previewRuntime = configurations.create("persistentPreviewRuntime") {
        isCanBeResolved = true
        isCanBeConsumed = false
    }
    dependencies.add(previewRuntime.name, dependencies.project(mapOf("path" to ":runtime-core", "configuration" to "runtimeElements")))
    val compilePortable = tasks.register<JavaCompile>("compilePersistentDemoPortableJava") {
        source(fixtureRoot.dir("src/main/java"))
        classpath = portableApi
        destinationDirectory.set(layout.buildDirectory.dir("classes/persistentDemo/portable"))
        javaCompiler.set(javaToolchainService.compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) })
        options.release.set(17)
    }
    val compileLauncher = tasks.register<JavaCompile>("compilePersistentDemoLauncherJava") {
        source(fixtureRoot.dir("src/loader/fabric/java"))
        classpath = mainSourceSet.get().compileClasspath + mainSourceSet.get().output + files(
            compilePreview.flatMap { it.destinationDirectory }, compilePortable.flatMap { it.destinationDirectory })
        destinationDirectory.set(layout.buildDirectory.dir("classes/persistentDemo/launcher"))
        javaCompiler.set(javaToolchainService.compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) })
        options.release.set(21)
    }
    val fixtureJar = tasks.register<org.gradle.jvm.tasks.Jar>("persistentDemoJar") {
        group = "development"
        description = "Builds the isolated development fixture; never published."
        archiveFileName.set("enderfall-persistent-preview-dev.jar")
        destinationDirectory.set(layout.buildDirectory.dir("persistentDemo"))
        from(compilePortable.flatMap { it.destinationDirectory }, compileLauncher.flatMap { it.destinationDirectory })
        from(fixtureRoot.dir("src/main/resources"))
        from(rootProject.layout.projectDirectory.file("LICENSE"))
    }
    if (providers.gradleProperty("enderfall.blockEntityPreview").orNull == "true") {
        tasks.withType<JavaExec>().matching { it.name == "runClient" }.configureEach {
            dependsOn(fixtureJar, compilePreview)
            classpath += files(compilePreview.flatMap { it.destinationDirectory }, fixtureJar.flatMap { it.archiveFile }) + previewRuntime
            systemProperty("fabric.addMods", fixtureJar.get().archiveFile.get().asFile.absolutePath)
            systemProperty("enderfall.persistence", providers.gradleProperty("enderfall.persistence").orElse("false").get())
        }
    }
    }
}

extensions.configure<SourceSetContainer> {
    named("main") {
        java.setSrcDirs(listOf(generatedBridgeSources))
        resources.setSrcDirs(listOf(generatedBridgeResources))
    }
}

val generateBridgeRuntime = tasks.register<JavaExec>("generateBridgeRuntime") {
    val persistence = providers.gradleProperty("enderfall.persistence").orElse("false").get()
    inputs.property("persistence", persistence)
    args("--persistence", persistence)
    group = "build"
    description = "Generates the complete ${generatedFabricTarget.id} runtime from the canonical bridge model."

    classpath = bridgeCompilerRuntime
    mainClass.set("uk.co.enderfall.sdk.bridge.BridgeCompilerMain")
    javaLauncher.set(javaToolchainService.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(generatedFabricTarget.javaVersion))
    })

    outputs.dir(generatedBridgeSources).withPropertyName("generatedBridgeSources")
    outputs.dir(generatedBridgeResources).withPropertyName("generatedBridgeResources")

    argumentProviders.add(objects.newInstance<GeneratedBridgeGenerationArguments>().apply {
        targetId.set(generatedFabricTarget.id)
        canonicalRoot.set(canonicalBridgeRoot)
        outputSources.set(generatedBridgeSources)
        outputResources.set(generatedBridgeResources)
    })
}

val verifyBridgeTargetCoordinates = tasks.register<JavaExec>("verifyBridgeTargetCoordinates") {
    group = "verification"
    description = "Verifies ${generatedFabricTarget.id} Gradle coordinates against the bridge target catalog."

    classpath = bridgeCompilerRuntime
    mainClass.set("uk.co.enderfall.sdk.bridge.TargetCoordinateMain")
    javaLauncher.set(javaToolchainService.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(generatedFabricTarget.javaVersion))
    })
    argumentProviders.add(objects.newInstance<GeneratedBridgeTargetCoordinateArguments>().apply {
        targetId.set(generatedFabricTarget.id)
        minecraftVersion.set(generatedFabricTarget.minecraftVersion)
        javaVersion.set(generatedFabricTarget.javaVersion)
        loader.set(generatedFabricTarget.loader)
        loaderVersion.set(generatedFabricTarget.fabricLoaderVersion)
        platformApiVersion.set(generatedFabricTarget.fabricApiVersion)
    })
}

@Suppress("UNCHECKED_CAST")
val loomRuns = requireNotNull(
    loomExtension.javaClass.methods.singleOrNull {
        it.name == "getRuns" && it.parameterCount == 0
    }?.invoke(loomExtension)
) { "Fabric Loom does not expose its run configurations" } as NamedDomainObjectContainer<Any>

fun configureGeneratedFabricRun(name: String, runDirectory: String, vararg arguments: String) {
    loomRuns.named(name).configure(Action<Any> {
        val settings = this
        val setRunDir = settings.javaClass.methods.singleOrNull {
            it.name == "setRunDir" && it.parameterTypes.contentEquals(arrayOf(String::class.java))
        } ?: throw GradleException("Fabric Loom run '$name' does not expose setRunDir(String)")
        setRunDir.invoke(settings, runDirectory)
        if (arguments.isNotEmpty()) {
            val programArgs = settings.javaClass.methods.singleOrNull {
                it.name == "programArgs" &&
                    it.parameterTypes.contentEquals(arrayOf(Array<String>::class.java))
            } ?: throw GradleException("Fabric Loom run '$name' does not expose programArgs(String...)")
            programArgs.invoke(settings, arguments)
        }
    })
}

configureGeneratedFabricRun("client", if (generatedFabricTarget.id == "1.21.4-fabric"
        && providers.gradleProperty("enderfall.blockEntityPreview").orNull == "true")
    "run/persistent-preview/client" else "run/client")
configureGeneratedFabricRun("server", "run/server", "nogui")

tasks.withType<JavaCompile>().configureEach {
    options.release.set(if (name == "compilePersistentDemoPortableJava") 17 else generatedFabricTarget.javaVersion)
    if (generatedFabricTarget.suppressAuxiliaryClassWarning) {
        options.compilerArgs.add("-Xlint:-auxiliaryclass")
    }
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(generateBridgeRuntime)
}

val sdkArtifactVersion = version.toString()
tasks.named<ProcessResources>("processResources") {
    dependsOn(generateBridgeRuntime)
    inputs.property("version", sdkArtifactVersion)
    expand(mapOf("version" to sdkArtifactVersion))
}

tasks.named("sourcesJar") {
    dependsOn(generateBridgeRuntime)
}
tasks.named("javadoc") {
    dependsOn(generateBridgeRuntime)
}

tasks.named("check") { dependsOn(verifyBridgeTargetCoordinates) }
if (providers.gradleProperty("enderfall.referenceRuntimes").orNull != "false") {
val referenceRuntimeProject = project(generatedFabricTarget.referenceProjectPath)
val referenceRuntimeRoot = rootProject.layout.projectDirectory.dir(
    generatedFabricTarget.referenceProjectPath.removePrefix(":")
)
val referenceJavaRoot = referenceRuntimeRoot.dir("src/main/java")
val referenceResourcesRoot = referenceRuntimeRoot.dir("src/main/resources")
val canonicalJavaRoot = canonicalBridgeRoot.dir("src/canonical/java")
val canonicalResourcesRoot = canonicalBridgeRoot.dir("src/canonical/resources")

val referenceCompileJava = referenceRuntimeProject.tasks.named<JavaCompile>("compileJava")
val referenceProcessResources = referenceRuntimeProject.tasks.named<ProcessResources>("processResources")
val referenceRemapJar = referenceRuntimeProject.tasks.named<AbstractArchiveTask>(
    generatedFabricTarget.runtimeArtifactTaskName
)
val generatedCompileJava = tasks.named<JavaCompile>("compileJava")
val generatedProcessResources = tasks.named<ProcessResources>("processResources")
val generatedRemapJar = tasks.named<AbstractArchiveTask>(generatedFabricTarget.runtimeArtifactTaskName)

val referenceClassesOutput = referenceCompileJava.flatMap { it.destinationDirectory }
val generatedClassesOutput = generatedCompileJava.flatMap { it.destinationDirectory }
val referenceProcessedResourcesOutput = layout.dir(referenceProcessResources.map { it.destinationDir })
val generatedProcessedResourcesOutput = layout.dir(generatedProcessResources.map { it.destinationDir })
val referenceRuntimeJarOutput = referenceRemapJar.flatMap { it.archiveFile }
val generatedRuntimeJarOutput = generatedRemapJar.flatMap { it.archiveFile }

val forbiddenJavaRoot = layout.projectDirectory.dir("src/main/java")
val forbiddenResourcesRoot = layout.projectDirectory.dir("src/main/resources")
val forbiddenJavaStateProvider = providers.of(GeneratedBridgePathExistenceValueSource::class) {
    parameters.path.set(forbiddenJavaRoot.asFile.absolutePath)
}
val forbiddenResourcesStateProvider = providers.of(GeneratedBridgePathExistenceValueSource::class) {
    parameters.path.set(forbiddenResourcesRoot.asFile.absolutePath)
}

val prepareEffectiveReferenceJava = if (
    generatedFabricTarget.referenceLayout == GeneratedFabricReferenceLayout.INHERITED_1_21_4
) {
    tasks.register<Sync>("prepareEffectiveReferenceJava") {
        group = "verification"
        description = "Builds the effective ${generatedFabricTarget.minecraftVersion} reference Java tree."
        duplicatesStrategy = DuplicatesStrategy.FAIL
        includeEmptyDirs = false

        from(rootProject.layout.projectDirectory.dir("runtime-fabric-1.21.4/src/main/java")) {
            include(generatedFabricTarget.inheritedReferenceJavaFiles)
        }
        from(referenceJavaRoot) {
            include(generatedFabricTarget.localReferenceJavaFiles)
        }
        into(layout.buildDirectory.dir("parity/effective-reference-java"))
    }
} else {
    null
}

val verifyGeneratedBridgeParity = tasks.register<JavaExec>("verifyGeneratedBridgeParity") {
    group = "verification"
    description = "Compares reference, generated, and compiled ${generatedFabricTarget.id} runtime trees."

    dependsOn(generateBridgeRuntime)
    dependsOn(generatedCompileJava, generatedProcessResources)
    dependsOn(referenceCompileJava, referenceProcessResources)
    prepareEffectiveReferenceJava?.let { dependsOn(it) }
    classpath = bridgeCompilerRuntime
    mainClass.set("uk.co.enderfall.sdk.bridge.BridgeParityMain")
    javaLauncher.set(javaToolchainService.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(generatedFabricTarget.javaVersion))
    })

    when (generatedFabricTarget.referenceLayout) {
        GeneratedFabricReferenceLayout.DIRECT -> {
            argumentProviders.add(objects.newInstance<GeneratedBridgeDirectParityArguments>().apply {
                forbiddenJava.set(forbiddenJavaRoot)
                forbiddenJavaState.set(forbiddenJavaStateProvider)
                forbiddenResources.set(forbiddenResourcesRoot)
                forbiddenResourcesState.set(forbiddenResourcesStateProvider)
                referenceJava.set(referenceJavaRoot)
                referenceResources.set(referenceResourcesRoot)
                canonicalJava.set(canonicalJavaRoot)
                canonicalResources.set(canonicalResourcesRoot)
                generatedJava.set(generatedBridgeSources)
                generatedResources.set(generatedBridgeResources)
                referenceClasses.set(referenceClassesOutput)
                generatedClasses.set(generatedClassesOutput)
                referenceProcessedResources.set(referenceProcessedResourcesOutput)
                generatedProcessedResources.set(generatedProcessedResourcesOutput)
            })
        }

        GeneratedFabricReferenceLayout.INHERITED_1_21_4 -> {
            val effectiveReferenceJavaOutput = layout.dir(
                prepareEffectiveReferenceJava!!.map { it.destinationDir }
            )
            argumentProviders.add(objects.newInstance<GeneratedBridgeInheritedParityArguments>().apply {
                forbiddenJava.set(forbiddenJavaRoot)
                forbiddenJavaState.set(forbiddenJavaStateProvider)
                forbiddenResources.set(forbiddenResourcesRoot)
                forbiddenResourcesState.set(forbiddenResourcesStateProvider)
                effectiveReferenceJava.set(effectiveReferenceJavaOutput)
                generatedJava.set(generatedBridgeSources)
                referenceClasses.set(referenceClassesOutput)
                generatedClasses.set(generatedClassesOutput)
                referenceSourcePackMetadata.set(referenceResourcesRoot.file("pack.mcmeta"))
                generatedSourcePackMetadata.set(generatedBridgeResources.map { it.file("pack.mcmeta") })
                referenceProcessedPackMetadata.set(
                    referenceProcessedResourcesOutput.map { it.file("pack.mcmeta") }
                )
                generatedProcessedPackMetadata.set(
                    generatedProcessedResourcesOutput.map { it.file("pack.mcmeta") }
                )
            })
        }
    }
}

val parityChecks = mutableListOf<Any>(verifyBridgeTargetCoordinates, verifyGeneratedBridgeParity)
if (!generatedFabricTarget.normalizeArtifactParity) {
    val verifyGeneratedBridgeArtifactParity = tasks.register<JavaExec>(
        "verifyGeneratedBridgeArtifactParity"
    ) {
        group = "verification"
        description = "Proves the generated remapped runtime JAR is byte-identical to the reference runtime JAR."

        dependsOn(generatedRemapJar, referenceRemapJar)
        classpath = bridgeCompilerRuntime
        mainClass.set("uk.co.enderfall.sdk.bridge.BridgeParityMain")
        javaLauncher.set(javaToolchainService.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(generatedFabricTarget.javaVersion))
        })

        argumentProviders.add(objects.newInstance<GeneratedBridgeArtifactParityArguments>().apply {
            referenceRuntimeJar.set(referenceRuntimeJarOutput)
            generatedRuntimeJar.set(generatedRuntimeJarOutput)
        })
    }
    parityChecks.add(verifyGeneratedBridgeArtifactParity)
} else {
    fun registerJarParityStage(
        taskName: String,
        runtimeJar: org.gradle.api.provider.Provider<org.gradle.api.file.RegularFile>,
        runtimeJarTask: org.gradle.api.tasks.TaskProvider<out org.gradle.api.Task>,
        destination: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>,
    ) = tasks.register<Sync>(taskName) {
        dependsOn(runtimeJarTask)
        duplicatesStrategy = DuplicatesStrategy.FAIL
        includeEmptyDirs = false
        into(destination)

        from(zipTree(runtimeJar)) {
            exclude("fabric.mod.json", "pack.mcmeta")
            into("entries")
        }
        from(zipTree(runtimeJar)) {
            include("pack.mcmeta")
            into("pack")
        }
        from(zipTree(runtimeJar)) {
            include("fabric.mod.json")
            into("ignored")
        }
    }

    val referenceJarStageRoot = layout.buildDirectory.dir("parity/remapped-jar/reference")
    val generatedJarStageRoot = layout.buildDirectory.dir("parity/remapped-jar/generated")
    val stageReferenceBridgeJar = registerJarParityStage(
        "stageReferenceBridgeJarForParity",
        referenceRuntimeJarOutput,
        referenceRemapJar,
        referenceJarStageRoot,
    )
    val stageGeneratedBridgeJar = registerJarParityStage(
        "stageGeneratedBridgeJarForParity",
        generatedRuntimeJarOutput,
        generatedRemapJar,
        generatedJarStageRoot,
    )
    val referenceJarStageOutput = layout.dir(stageReferenceBridgeJar.map { it.destinationDir })
    val generatedJarStageOutput = layout.dir(stageGeneratedBridgeJar.map { it.destinationDir })

    val verifyGeneratedBridgeArtifactParity = tasks.register<JavaExec>(
        "verifyGeneratedBridgeArtifactParity"
    ) {
        group = "verification"
        description = "Compares all remapped JAR entries except intentionally corrected Fabric metadata."

        dependsOn(stageReferenceBridgeJar, stageGeneratedBridgeJar)
        classpath = bridgeCompilerRuntime
        mainClass.set("uk.co.enderfall.sdk.bridge.BridgeParityMain")
        javaLauncher.set(javaToolchainService.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(generatedFabricTarget.javaVersion))
        })

        argumentProviders.add(objects.newInstance<GeneratedBridgeNormalizedArtifactParityArguments>().apply {
            referenceEntries.set(referenceJarStageOutput.map { it.dir("entries") })
            generatedEntries.set(generatedJarStageOutput.map { it.dir("entries") })
            referencePackMetadata.set(referenceJarStageOutput.map { it.file("pack/pack.mcmeta") })
            generatedPackMetadata.set(generatedJarStageOutput.map { it.file("pack/pack.mcmeta") })
            intentionallyDifferentReferenceMetadata.set(
                referenceJarStageOutput.map { it.file("ignored/fabric.mod.json") }
            )
            intentionallyDifferentGeneratedMetadata.set(
                generatedJarStageOutput.map { it.file("ignored/fabric.mod.json") }
            )
        })
    }
    parityChecks.add(verifyGeneratedBridgeArtifactParity)
}

tasks.named("check") {
    dependsOn(parityChecks)
}

}
apply(from = rootProject.file("gradle/persistence-fixture.gradle"))
