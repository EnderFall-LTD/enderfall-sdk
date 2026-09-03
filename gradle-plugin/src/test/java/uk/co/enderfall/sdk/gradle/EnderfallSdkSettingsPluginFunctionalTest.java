package uk.co.enderfall.sdk.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipFile;
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
        write("LICENSE", "CC0 test fixture\n");

        BuildResult result = runner("buildAll", "enderfallDoctor").build();

        assertEquals(TaskOutcome.SUCCESS, result.task(":buildAll").getOutcome());
        assertTrue(result.getOutput().contains("Development target: 1.21.4-fabric"));
        Path fabric = temporaryDirectory.resolve("build/releases/functional_mod-1.2.3+mc1.21.4-fabric.jar");
        Path neoForge = temporaryDirectory.resolve("build/releases/functional_mod-1.2.3+mc1.21.4-neoforge.jar");
        assertTrue(Files.isRegularFile(fabric));
        assertTrue(Files.isRegularFile(neoForge));
        try (ZipFile zip = new ZipFile(fabric.toFile())) {
            assertTrue(zip.getEntry("fabric.mod.json") != null);
            assertTrue(zip.getEntry("META-INF/enderfall.mod.json") != null);
            assertTrue(zip.getEntry("META-INF/enderfall/portable-source.sha256") != null);
        }
        try (ZipFile zip = new ZipFile(neoForge.toFile())) {
            assertTrue(zip.getEntry("META-INF/neoforge.mods.toml") != null);
            assertTrue(zip.getEntry("META-INF/enderfall.mod.json") != null);
        }
        try (ZipFile fabricZip = new ZipFile(fabric.toFile()); ZipFile neoForgeZip = new ZipFile(neoForge.toFile())) {
            byte[] fabricHash = fabricZip.getInputStream(
                    fabricZip.getEntry("META-INF/enderfall/portable-source.sha256")).readAllBytes();
            byte[] neoForgeHash = neoForgeZip.getInputStream(
                    neoForgeZip.getEntry("META-INF/enderfall/portable-source.sha256")).readAllBytes();
            assertTrue(java.util.Arrays.equals(fabricHash, neoForgeHash));
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
}
