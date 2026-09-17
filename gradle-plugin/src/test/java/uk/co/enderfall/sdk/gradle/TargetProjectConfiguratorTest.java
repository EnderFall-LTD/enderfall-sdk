package uk.co.enderfall.sdk.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.co.enderfall.sdk.gradle.model.ModDefinition;
import uk.co.enderfall.sdk.gradle.model.TargetDefinition;

class TargetProjectConfiguratorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void legacyProductionArchiveIsNamedAndCollectedInsteadOfDevelopmentJar() throws IOException {
        Project project = ProjectBuilder.builder()
                .withProjectDir(temporaryDirectory.resolve("target").toFile())
                .withName("generated_legacy_project")
                .build();
        ModDefinition mod = new ModDefinition();
        mod.setId("portable_mod");
        mod.setName("Portable Mod");
        mod.setGroup("dev.example");
        mod.setVersion("1.2.3");
        mod.setEntrypoint("dev.example.PortableMod");
        TargetDefinition target = new TargetDefinition("1.20.1", "forge", 17, "47.4.23", "");

        TargetProjectConfigurator.configure(project, temporaryDirectory.toFile(), mod, target, List.of(), false);
        Jar reobfJar = project.getTasks().register("reobfJar", Jar.class).get();
        Files.createDirectories(reobfJar.getArchiveFile().get().getAsFile().toPath().getParent());
        Files.write(reobfJar.getArchiveFile().get().getAsFile().toPath(), new byte[] {1});

        assertEquals("portable_mod-1.2.3+mc1.20.1-forge.jar", reobfJar.getArchiveFileName().get());
        Copy collector = (Copy) project.getTasks().getByName("collectArtifact");
        assertTrue(collector.getSource().getFiles().contains(reobfJar.getArchiveFile().get().getAsFile()));
    }

    @Test
    void workspaceSdkDependenciesAreChangingButThirdPartyDependenciesRemainImmutable() {
        Project project = ProjectBuilder.builder()
                .withProjectDir(temporaryDirectory.resolve("workspace-target").toFile())
                .withName("generated_workspace_project")
                .build();
        ModDefinition mod = new ModDefinition();
        mod.setId("portable_mod");
        mod.setName("Portable Mod");
        mod.setGroup("dev.example");
        mod.setVersion("1.2.3");
        mod.setEntrypoint("dev.example.PortableMod");
        TargetDefinition target = new TargetDefinition("1.20.1", "forge", 17, "47.4.23", "");

        TargetProjectConfigurator.configure(project, temporaryDirectory.toFile(), mod, target, List.of(), true);
        ExternalModuleDependency thirdParty = (ExternalModuleDependency) project.getDependencies()
                .add("implementation", "dev.example:immutable-library:1.0.0");

        List<ExternalModuleDependency> sdkDependencies = project.getConfigurations().stream()
                .flatMap(configuration -> configuration.getDependencies().stream())
                .filter(ExternalModuleDependency.class::isInstance)
                .map(ExternalModuleDependency.class::cast)
                .filter(dependency -> "uk.co.enderfall.sdk".equals(dependency.getGroup()))
                .distinct()
                .toList();
        assertFalse(sdkDependencies.isEmpty());
        assertTrue(sdkDependencies.stream().allMatch(ExternalModuleDependency::isChanging));
        assertFalse(thirdParty.isChanging());
    }
}
