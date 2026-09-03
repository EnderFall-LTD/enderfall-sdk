package uk.co.enderfall.sdk.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.initialization.Settings;
import org.gradle.api.plugins.BasePlugin;
import uk.co.enderfall.sdk.gradle.model.EnderfallSdkExtension;
import uk.co.enderfall.sdk.gradle.model.ModDefinition;
import uk.co.enderfall.sdk.gradle.model.TargetCatalog;
import uk.co.enderfall.sdk.gradle.model.TargetDefinition;
import uk.co.enderfall.sdk.gradle.model.VersionDefinition;

/** Settings plugin that materializes one isolated target project per selected matrix entry. */
public final class EnderfallSdkSettingsPlugin implements Plugin<Settings> {
    private static final String TARGET_PARENT = ":enderfallTargets";

    @Override
    public void apply(Settings settings) {
        settings.getPluginManager().apply("org.gradle.toolchains.foojay-resolver-convention");
        EnderfallSdkExtension extension = settings.getExtensions()
                .create("enderfallSdk", EnderfallSdkExtension.class);
        settings.getDependencyResolutionManagement().getRepositories().mavenCentral();
        settings.getGradle().settingsEvaluated(ignored -> createProjects(settings, extension));
    }

    private static void createProjects(Settings settings, EnderfallSdkExtension extension) {
        validateMod(extension.modDefinition());
        Map<String, TargetDefinition> selected = selectTargets(extension);
        if (!selected.containsKey(extension.getDevelopmentTarget())) {
            throw new GradleException("developmentTarget " + extension.getDevelopmentTarget()
                    + " is not selected. Selected targets: " + String.join(", ", selected.keySet()));
        }

        File generatedRoot = new File(settings.getRootDir(), ".gradle/enderfall-sdk/projects");
        createDirectory(generatedRoot);
        settings.include(TARGET_PARENT);
        ProjectDescriptor parent = settings.project(TARGET_PARENT);
        parent.setProjectDir(new File(generatedRoot, "container"));
        createDirectory(parent.getProjectDir());

        Map<String, TargetDefinition> byProjectPath = new LinkedHashMap<>();
        selected.values().forEach(target -> {
            String path = TARGET_PARENT + ':' + target.projectName();
            settings.include(path);
            ProjectDescriptor descriptor = settings.project(path);
            descriptor.setProjectDir(new File(generatedRoot, target.projectName()));
            createDirectory(descriptor.getProjectDir());
            byProjectPath.put(path, target);
        });

        settings.getGradle().beforeProject(project -> {
            TargetDefinition target = byProjectPath.get(project.getPath());
            if (target != null) {
                TargetProjectConfigurator.configure(project, settings.getRootDir(), extension.modDefinition(), target);
            } else if (project == project.getRootProject()) {
                configureRoot(project, extension, selected);
            }
        });
    }

    private static Map<String, TargetDefinition> selectTargets(EnderfallSdkExtension extension) {
        Map<String, TargetDefinition> selected = new LinkedHashMap<>();
        for (VersionDefinition version : extension.targetsDefinition().versions()) {
            if (version.loaderIds().isEmpty()) {
                throw new GradleException("Minecraft " + version.minecraftVersion() + " has no selected loaders");
            }
            for (String loader : version.loaderIds()) {
                TargetDefinition target = TargetCatalog.find(version.minecraftVersion(), loader)
                        .orElseThrow(() -> new GradleException("Unsupported EnderFall target "
                                + version.minecraftVersion() + '-' + loader + ". Supported targets: "
                                + TargetCatalog.supportedIds()));
                if (selected.putIfAbsent(target.id(), target) != null) {
                    throw new GradleException("Duplicate EnderFall target " + target.id());
                }
            }
        }
        if (selected.isEmpty()) {
            throw new GradleException("No EnderFall targets selected. Add enderfallSdk { targets { version(...) } }");
        }
        return selected;
    }

    private static void configureRoot(Project project, EnderfallSdkExtension extension,
                                      Map<String, TargetDefinition> selected) {
        project.getPluginManager().apply(BasePlugin.class);
        registerAggregate(project, "buildAll", "Builds and collects every selected target.", selected,
                "collectArtifact");
        registerAggregate(project, "checkAll", "Checks every selected target.", selected, "check");

        String selectedTarget = Objects.toString(project.findProperty("enderfall.target"),
                extension.getDevelopmentTarget());
        TargetDefinition development = selected.get(selectedTarget);
        if (development == null) {
            throw new GradleException("Unknown -Penderfall.target=" + selectedTarget + ". Selected targets: "
                    + String.join(", ", selected.keySet()));
        }
        registerProxy(project, "runClient", development, "runClient");
        registerProxy(project, "runServer", development, "runServer");
        registerProxy(project, "generateData", development, "generateData");

        project.getTasks().register("enderfallDoctor", task -> {
            task.setGroup("enderfall sdk");
            task.setDescription("Prints the resolved EnderFall SDK configuration and target matrix.");
            task.doLast(ignored -> {
                project.getLogger().lifecycle("EnderFall SDK {}", TargetCatalog.SDK_VERSION);
                project.getLogger().lifecycle("Mod: {} ({})", extension.modDefinition().getName(),
                        extension.modDefinition().getId());
                project.getLogger().lifecycle("Development target: {}", development.id());
                for (TargetDefinition target : selected.values()) {
                    project.getLogger().lifecycle("  {} | Java {} | loader {} | API {} | runtime UNVALIDATED",
                            target.id(),
                            target.javaVersion(), target.loaderVersion(),
                            target.platformApiVersion().isBlank() ? "included" : target.platformApiVersion());
                }
            });
        });
    }

    private static void registerAggregate(Project root, String name, String description,
                                          Map<String, TargetDefinition> targets, String targetTask) {
        root.getTasks().register(name, task -> {
            task.setGroup(targetTask.equals("check") ? "verification" : "build");
            task.setDescription(description);
            targets.values().forEach(target -> task.dependsOn(projectPath(target) + ':' + targetTask));
        });
    }

    private static void registerProxy(Project root, String name, TargetDefinition target, String targetTask) {
        root.getTasks().register(name, task -> {
            task.setGroup("enderfall sdk");
            task.setDescription("Runs " + targetTask + " for " + target.id());
            task.dependsOn(projectPath(target) + ':' + targetTask);
        });
    }

    private static String projectPath(TargetDefinition target) {
        return TARGET_PARENT + ':' + target.projectName();
    }

    private static void validateMod(ModDefinition mod) {
        if (!mod.getId().matches("[a-z][a-z0-9_]{1,63}")) {
            throw new GradleException("Invalid mod id " + mod.getId()
                    + "; use 2-64 lowercase letters, digits, and underscores");
        }
        if (mod.getName().isBlank()) {
            throw new GradleException("Mod name cannot be blank");
        }
        if (!mod.getGroup().matches("[a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)+")) {
            throw new GradleException("Invalid Java package group " + mod.getGroup());
        }
        validateClassName("entrypoint", mod.getEntrypoint());
        if (!mod.getClientEntrypoint().isBlank()) {
            validateClassName("clientEntrypoint", mod.getClientEntrypoint());
        }
        if (mod.getVersion().isBlank()) {
            throw new GradleException("Mod version cannot be blank");
        }
    }

    private static void validateClassName(String field, String className) {
        if (!className.matches("[a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)+")) {
            throw new GradleException("Invalid " + field + " class " + className);
        }
    }

    private static void createDirectory(File directory) {
        try {
            Files.createDirectories(directory.toPath());
        } catch (IOException exception) {
            throw new GradleException("Cannot create generated target directory " + directory, exception);
        }
    }
}
