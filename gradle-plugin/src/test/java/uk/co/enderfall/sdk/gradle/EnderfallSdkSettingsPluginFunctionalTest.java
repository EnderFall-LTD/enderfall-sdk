package uk.co.enderfall.sdk.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipFile;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnderfallSdkSettingsPluginFunctionalTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void freshConsumerBuildProducesNamedTargetJarsAndMetadata() throws IOException {
        Path repository = temporaryDirectory.resolve("repository");
        installApiStub(repository);
        installRuntimeCoreStub(repository);
        installFabricRuntimeStub(repository);
        installNeoForgeRuntimeStub(repository);
        write("settings.gradle.kts", """
                pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
                plugins { id("uk.co.enderfall.sdk") }
                dependencyResolutionManagement {
                    repositories {
                        maven { url = uri("%s") }
                        mavenCentral()
                    }
                }
                rootProject.name = "functional-consumer"
                enderfallSdk {
                    mod {
                        id = "functional_mod"
                        name = "Functional Mod"
                        group = "dev.example"
                        version = "1.2.3"
                        entrypoint = "dev.example.FunctionalMod"
                        author = "EnderFall"
                        license = "CC0-1.0"
                    }
                    targets { version("1.21.4") { loaders("fabric", "neoforge") } }
                    developmentTarget = "1.21.4-fabric"
                }
                """.formatted(repository.toUri()));
        write("build.gradle.kts", "");
        write("src/main/java/dev/example/FunctionalMod.java", """
                package dev.example;
                public final class FunctionalMod { }
                """);
        write("src/main/resources/assets/functional_mod/models/item/example.json", "{}\n");
        write("LICENSE", "CC0 test fixture\n");

        BuildResult result = runner("buildAll", "enderfallDoctor", "--configuration-cache").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":buildAll").getOutcome());
        assertTrue(result.getOutput().contains("Development target: 1.21.4-fabric"));
        Path fabric = temporaryDirectory.resolve("build/releases/functional_mod-1.2.3+mc1.21.4-fabric.jar");
        Path neoForge = temporaryDirectory.resolve("build/releases/functional_mod-1.2.3+mc1.21.4-neoforge.jar");
        assertTrue(Files.isRegularFile(fabric));
        assertTrue(Files.isRegularFile(neoForge));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve(
                ".gradle/enderfall-sdk/projects/1_21_4_fabric/build/resources/main/"
                        + "assets/functional_mod/models/item/example.json")));
        try (JarFile zip = new JarFile(fabric.toFile())) {
            assertTrue(zip.getEntry("fabric.mod.json") != null);
            assertTrue(zip.getEntry("META-INF/enderfall.mod.json") != null);
            assertTrue(zip.getEntry("META-INF/enderfall/portable-source.sha256") != null);
            assertTrue(zip.getEntry("pack.mcmeta") != null);
            assertTrue(zip.getEntry("assets/functional_mod/models/item/example.json") != null);
            assertEquals("COMPILE_VALIDATED", zip.getManifest().getMainAttributes()
                    .getValue("EnderFall-Runtime-Status"));
        }
        try (ZipFile zip = new ZipFile(neoForge.toFile())) {
            assertTrue(zip.getEntry("META-INF/neoforge.mods.toml") != null);
            assertTrue(zip.getEntry("META-INF/enderfall.mod.json") != null);
            assertTrue(zip.getEntry("pack.mcmeta") != null);
        }
        try (ZipFile fabricZip = new ZipFile(fabric.toFile()); ZipFile neoForgeZip = new ZipFile(neoForge.toFile())) {
            byte[] fabricHash = fabricZip.getInputStream(
                    fabricZip.getEntry("META-INF/enderfall/portable-source.sha256")).readAllBytes();
            byte[] neoForgeHash = neoForgeZip.getInputStream(
                    neoForgeZip.getEntry("META-INF/enderfall/portable-source.sha256")).readAllBytes();
            assertTrue(java.util.Arrays.equals(fabricHash, neoForgeHash));
        }

        // The first repeat records newly created generated source/resource directories as stable inputs.
        runner("buildAll", "enderfallDoctor", "--configuration-cache").build();
        BuildResult cached = runner("buildAll", "enderfallDoctor", "--configuration-cache").build();
        assertTrue(cached.getOutput().contains("Reusing configuration cache."));
    }

    @Test
    void initializationTaskDoesNotMaterializeLoaderProjects() throws IOException {
        write("settings.gradle.kts", """
                plugins { id("uk.co.enderfall.sdk") }
                rootProject.name = "initialization-fixture"
                enderfallSdk {
                    mod {
                        id = "fixture_mod"
                        name = "Fixture Mod"
                        group = "dev.fixture"
                        version = "1.0.0"
                        entrypoint = "dev.fixture.FixtureMod"
                    }
                    targets {
                        version("1.21.4") { loaders("fabric", "neoforge") }
                        version("26.2") { loaders("fabric", "neoforge") }
                    }
                    developmentTarget = "26.2-fabric"
                }
                """);
        write("build.gradle.kts", """
                tasks.register("initializeMod") {
                    doLast { println("INITIALIZATION_ONLY") }
                }
                """);

        BuildResult result = runner("initializeMod").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":initializeMod").getOutcome());
        assertTrue(result.getOutput().contains("INITIALIZATION_ONLY"));
        assertTrue(!result.getOutput().contains("Fabric Loom:"));
    }

    @Test
    void legacyTargetsExposeNativePreparationProxiesWithoutLaunchingMinecraft() throws IOException {
        write("settings.gradle.kts", """
                plugins { id("uk.co.enderfall.sdk") }
                rootProject.name = "legacy-preparation-fixture"
                enderfallSdk {
                    targets { version("1.20.1") { loaders("forge", "neoforge") } }
                    developmentTarget = "1.20.1-forge"
                }
                """);
        write("build.gradle.kts", """
                // Initialization intentionally avoids materializing native target projects.
                val target = providers.gradleProperty("enderfall.target").get().replace('.', '_').replace('-', '_')
                check(tasks.named("prepareClient").get().dependsOn == setOf(
                    ":enderfallTargets:$target:createMinecraftArtifacts", ":enderfallTargets:$target:prepareClientRun"))
                check(tasks.named("prepareServer").get().dependsOn == setOf(
                    ":enderfallTargets:$target:createMinecraftArtifacts", ":enderfallTargets:$target:prepareServerRun"))
                tasks.register("initializeMod")
                """);
        for (String loader : java.util.List.of("forge", "neoforge")) {
            BuildResult result = runner("initializeMod", "-Penderfall.target=1.20.1-" + loader).build();
            assertEquals(TaskOutcome.UP_TO_DATE, result.task(":initializeMod").getOutcome());
        }
    }

    @Test
    void unsupportedCombinationListsValidTargets() throws IOException {
        write("settings.gradle.kts", """
                plugins { id("uk.co.enderfall.sdk") }
                enderfallSdk {
                    targets { version("1.19.4") { loaders("quilt") } }
                    developmentTarget = "1.19.4-quilt"
                }
                """);
        write("build.gradle.kts", "");

        BuildResult result = runner("tasks").buildAndFail();

        assertTrue(result.getOutput().contains("Unsupported EnderFall target 1.19.4-quilt"));
        assertTrue(result.getOutput().contains("1.21.4-fabric"));
        assertTrue(result.getOutput().contains("26.2-neoforge"));
    }

    @Test
    void portableMinecraftImportFailsBeforeCompilation() throws IOException {
        Path repository = temporaryDirectory.resolve("repository");
        installApiStub(repository);
        installFabricRuntimeStub(repository);
        write("settings.gradle.kts", """
                pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
                plugins { id("uk.co.enderfall.sdk") }
                dependencyResolutionManagement { repositories { maven { url = uri("%s") } } }
                enderfallSdk {
                    targets { version("1.21.4") { loaders("fabric") } }
                    developmentTarget = "1.21.4-fabric"
                }
                """.formatted(repository.toUri()));
        write("build.gradle.kts", "");
        write("src/main/java/com/example/ExampleMod.java", """
                package com.example;
                import net.minecraft.world.item.Item;
                public final class ExampleMod { Item item; }
                """);

        BuildResult result = runner("checkAll").buildAndFail();

        assertTrue(result.getOutput().contains("contains forbidden token import net.minecraft."));
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(temporaryDirectory.toFile())
                .withArguments(arguments)
                .withPluginClasspath()
                .forwardOutput();
    }

    private void write(String relativePath, String content) throws IOException {
        Path path = temporaryDirectory.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static void installApiStub(Path repository) throws IOException {
        Path artifactDirectory = repository.resolve(
                "uk/co/enderfall/sdk/enderfall-sdk-api/0.1.0-beta.1");
        Files.createDirectories(artifactDirectory);
        Path jar = artifactDirectory.resolve("enderfall-sdk-api-0.1.0-beta.1.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            // A valid empty JAR is sufficient because this fixture deliberately imports no API types.
            output.flush();
        }
        Files.writeString(artifactDirectory.resolve("enderfall-sdk-api-0.1.0-beta.1.pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>uk.co.enderfall.sdk</groupId>
                  <artifactId>enderfall-sdk-api</artifactId>
                  <version>0.1.0-beta.1</version>
                </project>
                """, StandardCharsets.UTF_8);
    }

    private static void installFabricRuntimeStub(Path repository) throws IOException {
        Path workDirectory = Files.createTempDirectory(repository.getParent(), "fabric-runtime-stub-");
        Path source = workDirectory.resolve(
                "src/uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricConsumerBootstrap.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package uk.co.enderfall.sdk.runtime.fabric.v1_21_4;
                public final class FabricConsumerBootstrap {
                    private FabricConsumerBootstrap() { }
                    public static void initialize(String modId, String entrypoint, String clientEntrypoint) { }
                }
                """, StandardCharsets.UTF_8);
        Path classes = workDirectory.resolve("classes");
        Files.createDirectories(classes);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null, "--release", "17", "-d", classes.toString(), source.toString());
        if (result != 0) {
            throw new IOException("Could not compile the Fabric runtime test stub");
        }

        Path artifactDirectory = repository.resolve(
                "uk/co/enderfall/sdk/enderfall-sdk-runtime-1.21.4-fabric/0.1.0-beta.1");
        Files.createDirectories(artifactDirectory);
        Path jar = artifactDirectory.resolve("enderfall-sdk-runtime-1.21.4-fabric-0.1.0-beta.1.jar");
        Path classFile = classes.resolve(
                "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricConsumerBootstrap.class");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(
                    "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricConsumerBootstrap.class"));
            output.write(Files.readAllBytes(classFile));
            output.closeEntry();
            output.putNextEntry(new JarEntry("fabric.mod.json"));
            output.write("""
                    {"schemaVersion":1,"id":"enderfall_sdk","version":"0.1.0-beta.1","name":"EnderFall SDK"}
                    """.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        Files.writeString(artifactDirectory.resolve(
                "enderfall-sdk-runtime-1.21.4-fabric-0.1.0-beta.1.pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>uk.co.enderfall.sdk</groupId>
                  <artifactId>enderfall-sdk-runtime-1.21.4-fabric</artifactId>
                  <version>0.1.0-beta.1</version>
                </project>
                """, StandardCharsets.UTF_8);
    }

    private static void installRuntimeCoreStub(Path repository) throws IOException {
        Path workDirectory = Files.createTempDirectory(repository.getParent(), "runtime-core-stub-");
        Path source = workDirectory.resolve(
                "src/uk/co/enderfall/sdk/runtime/data/PortableDataGeneratorMain.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package uk.co.enderfall.sdk.runtime.data;
                public final class PortableDataGeneratorMain {
                    private PortableDataGeneratorMain() { }
                    public static void main(String[] arguments) { }
                }
                """, StandardCharsets.UTF_8);
        Path classes = workDirectory.resolve("classes");
        Files.createDirectories(classes);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null, "--release", "17", "-d", classes.toString(), source.toString());
        if (result != 0) {
            throw new IOException("Could not compile the runtime-core test stub");
        }

        Path artifactDirectory = repository.resolve(
                "uk/co/enderfall/sdk/enderfall-sdk-runtime-core/0.1.0-beta.1");
        Files.createDirectories(artifactDirectory);
        Path jar = artifactDirectory.resolve("enderfall-sdk-runtime-core-0.1.0-beta.1.jar");
        Path classFile = classes.resolve(
                "uk/co/enderfall/sdk/runtime/data/PortableDataGeneratorMain.class");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(
                    "uk/co/enderfall/sdk/runtime/data/PortableDataGeneratorMain.class"));
            output.write(Files.readAllBytes(classFile));
            output.closeEntry();
        }
        Files.writeString(artifactDirectory.resolve(
                "enderfall-sdk-runtime-core-0.1.0-beta.1.pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>uk.co.enderfall.sdk</groupId>
                  <artifactId>enderfall-sdk-runtime-core</artifactId>
                  <version>0.1.0-beta.1</version>
                </project>
                """, StandardCharsets.UTF_8);
    }

    private static void installNeoForgeRuntimeStub(Path repository) throws IOException {
        Path workDirectory = Files.createTempDirectory(repository.getParent(), "neoforge-runtime-stub-");
        Path source = workDirectory.resolve(
                "src/uk/co/enderfall/sdk/runtime/neoforge/v1_21_4/NeoForgeConsumerBootstrap.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package uk.co.enderfall.sdk.runtime.neoforge.v1_21_4;
                public final class NeoForgeConsumerBootstrap {
                    private NeoForgeConsumerBootstrap() { }
                    public static void initialize(String modId, String entrypoint, String clientEntrypoint,
                                                  Object modBus, ClassLoader consumerClassLoader) { }
                }
                """, StandardCharsets.UTF_8);
        Path classes = workDirectory.resolve("classes");
        Files.createDirectories(classes);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null, "--release", "17", "-d", classes.toString(), source.toString());
        if (result != 0) {
            throw new IOException("Could not compile the NeoForge runtime test stub");
        }

        Path artifactDirectory = repository.resolve(
                "uk/co/enderfall/sdk/enderfall-sdk-runtime-1.21.4-neoforge/0.1.0-beta.1");
        Files.createDirectories(artifactDirectory);
        Path jar = artifactDirectory.resolve("enderfall-sdk-runtime-1.21.4-neoforge-0.1.0-beta.1.jar");
        Path classFile = classes.resolve(
                "uk/co/enderfall/sdk/runtime/neoforge/v1_21_4/NeoForgeConsumerBootstrap.class");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(
                    "uk/co/enderfall/sdk/runtime/neoforge/v1_21_4/NeoForgeConsumerBootstrap.class"));
            output.write(Files.readAllBytes(classFile));
            output.closeEntry();
            output.putNextEntry(new JarEntry("META-INF/neoforge.mods.toml"));
            output.write("""
                    modLoader="javafml"
                    loaderVersion="[1,)"
                    license="Apache-2.0"
                    [[mods]]
                    modId="enderfall_sdk"
                    version="0.1.0-beta.1"
                    displayName="EnderFall SDK"
                    """.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        Files.writeString(artifactDirectory.resolve(
                "enderfall-sdk-runtime-1.21.4-neoforge-0.1.0-beta.1.pom"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>uk.co.enderfall.sdk</groupId>
                  <artifactId>enderfall-sdk-runtime-1.21.4-neoforge</artifactId>
                  <version>0.1.0-beta.1</version>
                </project>
                """, StandardCharsets.UTF_8);
    }
}
