package uk.co.enderfall.sdk.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.initialization.Settings;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import uk.co.enderfall.sdk.gradle.model.EnderfallSdkExtension;
import uk.co.enderfall.sdk.gradle.model.ModDefinition;
import uk.co.enderfall.sdk.gradle.model.TargetCatalog;
import uk.co.enderfall.sdk.gradle.model.TargetDefinition;
import uk.co.enderfall.sdk.gradle.model.VersionDefinition;
import uk.co.enderfall.sdk.gradle.task.EnderfallDoctorTask;

/** Settings plugin that materializes one isolated target project per selected matrix entry. */
public final class EnderfallSdkSettingsPlugin implements Plugin<Settings> {
    private static final String TARGET_PARENT = ":enderfallTargets";

    @Override
    public void apply(Settings settings) {
        settings.getPluginManager().apply("org.gradle.toolchains.foojay-resolver-convention");
        configureLoaderRepositories(settings);
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
        Map<String, TargetDefinition> materialized = materializedTargets(settings, extension, selected);

        File generatedRoot = new File(settings.getRootDir(), ".gradle/enderfall-sdk/projects");
        createDirectory(generatedRoot);
        settings.include(TARGET_PARENT);
        ProjectDescriptor parent = settings.project(TARGET_PARENT);
        parent.setProjectDir(new File(generatedRoot, "container"));
        createDirectory(parent.getProjectDir());

        Map<String, TargetDefinition> byProjectPath = new LinkedHashMap<>();
        java.util.List<URI> dependencyRepositories = new ArrayList<>();
        settings.getDependencyResolutionManagement().getRepositories()
                .withType(MavenArtifactRepository.class)
                .forEach(repository -> dependencyRepositories.add(repository.getUrl()));
        materialized.values().forEach(target -> {
            String path = TARGET_PARENT + ':' + target.projectName();
            settings.include(path);
            ProjectDescriptor descriptor = settings.project(path);
            descriptor.setProjectDir(new File(generatedRoot, target.projectName()));
            createDirectory(descriptor.getProjectDir());
            writeTargetBuild(descriptor.getProjectDir(), target);
            byProjectPath.put(path, target);
        });

        settings.getGradle().beforeProject(project -> {
            TargetDefinition target = byProjectPath.get(project.getPath());
            if (target != null) {
                boolean workspaceDevelopment = settings.getProviders()
                        .gradleProperty("enderfall.workspaceRepository").isPresent();
                TargetProjectConfigurator.configure(project, settings.getRootDir(), extension.modDefinition(), target,
                        dependencyRepositories, workspaceDevelopment);
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
        if (development.loader().equals("neoforge") || development.loader().equals("forge")) {
            registerProxy(project, "prepareClient", development,
                    "createMinecraftArtifacts", "prepareClientRun");
            registerProxy(project, "prepareServer", development,
                    "createMinecraftArtifacts", "prepareServerRun");
        }

        java.util.List<String> doctorLines = new ArrayList<>();
        doctorLines.add("EnderFall SDK " + TargetCatalog.SDK_VERSION);
        doctorLines.add("Mod: " + extension.modDefinition().getName()
                + " (" + extension.modDefinition().getId() + ')');
        doctorLines.add("Development target: " + development.id());
        for (TargetDefinition target : selected.values()) {
            String runtimeStatus = TargetCatalog.hasRuntimeAdapter(target)
                    ? "COMPILE_VALIDATED" : "UNAVAILABLE";
            doctorLines.add("  " + target.id() + " | Java " + target.javaVersion()
                    + " | loader " + target.loaderVersion() + " | API "
                    + (target.platformApiVersion().isBlank() ? "included" : target.platformApiVersion())
                    + " | runtime " + runtimeStatus);
        }
        project.getTasks().register("enderfallDoctor", EnderfallDoctorTask.class, task -> {
            task.setGroup("enderfall sdk");
            task.setDescription("Prints the resolved EnderFall SDK configuration and target matrix.");
            task.getLines().set(doctorLines);
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

    private static void registerProxy(Project root, String name, TargetDefinition target, String... targetTasks) {
        root.getTasks().register(name, task -> {
            task.setGroup("enderfall sdk");
            task.setDescription("Runs " + String.join(" and ", targetTasks) + " for " + target.id());
            for (String targetTask : targetTasks) {
                task.dependsOn(projectPath(target) + ':' + targetTask);
            }
        });
    }

    private static Map<String, TargetDefinition> materializedTargets(Settings settings,
                                                                      EnderfallSdkExtension extension,
                                                                      Map<String, TargetDefinition> selected) {
        java.util.Set<String> requested = settings.getGradle().getStartParameter().getTaskNames().stream()
                .map(name -> name.substring(name.lastIndexOf(':') + 1))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (requested.equals(java.util.Set.of("initializeMod"))) {
            return java.util.Map.of();
        }
        boolean runtimeInvocation = requested.stream()
                .anyMatch(java.util.Set.of(
                        "runClient", "runServer", "generateData", "prepareClient", "prepareServer")::contains);
        boolean aggregateInvocation = requested.stream()
                .anyMatch(java.util.Set.of("buildAll", "checkAll")::contains);
        if (!runtimeInvocation) {
            return selected;
        }
        if (aggregateInvocation) {
            throw new GradleException(
                    "Run runClient, runServer, generateData, prepareClient, or prepareServer "
                            + "separately from buildAll/checkAll");
        }
        String selectedTarget = settings.getProviders().gradleProperty("enderfall.target")
                .getOrElse(extension.getDevelopmentTarget());
        TargetDefinition target = selected.get(selectedTarget);
        if (target == null) {
            throw new GradleException("Unknown -Penderfall.target=" + selectedTarget + ". Selected targets: "
                    + String.join(", ", selected.keySet()));
        }
        Map<String, TargetDefinition> result = new LinkedHashMap<>();
        result.put(target.id(), target);
        return result;
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

    private static void configureLoaderRepositories(Settings settings) {
        settings.getPluginManagement().getRepositories().maven(repository -> {
            repository.setName("Fabric");
            repository.setUrl("https://maven.fabricmc.net/");
            repository.content(content -> content.includeGroupByRegex("net\\.fabricmc(?:\\..*)?"));
        });
        settings.getPluginManagement().getRepositories().maven(repository -> {
            repository.setName("NeoForged");
            repository.setUrl("https://maven.neoforged.net/releases");
            repository.content(content -> content.includeGroupByRegex("net\\.neoforged(?:\\..*)?"));
        });
        settings.getDependencyResolutionManagement().getRepositories().maven(repository -> {
            repository.setName("Fabric");
            repository.setUrl("https://maven.fabricmc.net/");
            repository.content(content -> content.includeGroupByRegex("net\\.fabricmc(?:\\..*)?"));
        });
        settings.getDependencyResolutionManagement().getRepositories().maven(repository -> {
            repository.setName("NeoForged");
            repository.setUrl("https://maven.neoforged.net/releases");
            repository.content(content -> {
                content.includeGroupByRegex("net\\.neoforged(?:\\..*)?");
                content.includeGroup("net.minecraftforge");
            });
        });
    }

    private static void writeTargetBuild(File directory, TargetDefinition target) {
        String script;
        if (target.loader().equals("fabric")
                && (target.minecraftVersion().equals("1.20.1")
                || target.minecraftVersion().equals("1.21.1")
                || target.minecraftVersion().equals("1.21.4"))) {
            String minecraftVersion = target.minecraftVersion();
            String runtimeArtifact = "enderfall-sdk-runtime-" + minecraftVersion + "-fabric";
            script = "plugins {\n"
                    + "    id 'net.fabricmc.fabric-loom-remap' version '1.17.20'\n"
                    + "}\n\n"
                    + autoConnectVariables()
                    + "dependencies {\n"
                    + "    minecraft 'com.mojang:minecraft:" + minecraftVersion + "'\n"
                    + "    mappings loom.officialMojangMappings()\n"
                    + "    modImplementation 'net.fabricmc:fabric-loader:" + target.loaderVersion() + "'\n"
                    + "    modImplementation 'net.fabricmc.fabric-api:fabric-api:"
                    + target.platformApiVersion() + "'\n"
                    + "    modImplementation 'uk.co.enderfall.sdk:" + runtimeArtifact + ":"
                    + TargetCatalog.SDK_VERSION + "'\n"
                    + "    runtimeOnly 'uk.co.enderfall.sdk:enderfall-sdk-runtime-core:"
                    + TargetCatalog.SDK_VERSION + "'\n"
                    + "}\n\n"
                    + "loom {\n"
                    + "    runs {\n"
                    + fabricClientRun()
                    + "        server { runDir 'run/server'; programArgs 'nogui' }\n"
                    + "    }\n"
                    + "}\n\n"
                    + "tasks.named('runServer').configure { standardInput = System.in }\n";
        } else if (target.loader().equals("fabric") && target.minecraftVersion().equals("26.2")) {
            script = "plugins {\n"
                    + "    id 'net.fabricmc.fabric-loom' version '1.17.20'\n"
                    + "}\n\n"
                    + autoConnectVariables()
                    + "dependencies {\n"
                    + "    minecraft 'com.mojang:minecraft:26.2'\n"
                    + "    implementation 'net.fabricmc:fabric-loader:0.19.5'\n"
                    + "    implementation 'net.fabricmc.fabric-api:fabric-api:0.159.0+26.2'\n"
                    + "    implementation 'uk.co.enderfall.sdk:enderfall-sdk-runtime-26.2-fabric:"
                    + TargetCatalog.SDK_VERSION + "'\n"
                    + "    runtimeOnly 'uk.co.enderfall.sdk:enderfall-sdk-runtime-core:"
                    + TargetCatalog.SDK_VERSION + "'\n"
                    + "}\n\n"
                    + "loom {\n"
                    + "    runs {\n"
                    + fabricClientRun()
                    + "        server { runDir 'run/server'; programArgs 'nogui' }\n"
                    + "    }\n"
                    + "}\n\n"
                    + "tasks.named('runServer').configure { standardInput = System.in }\n";
        } else if (target.minecraftVersion().equals("1.20.1")
                && (target.loader().equals("forge") || target.loader().equals("neoforge"))) {
            String runtimeArtifact = "enderfall-sdk-runtime-1.20.1-" + target.loader();
            String legacyVersion = target.loader().equals("forge")
                    ? "    version = '1.20.1-" + target.loaderVersion() + "'\n"
                    : "    enable { neoForgeVersion = '1.20.1-" + target.loaderVersion() + "' }\n";
            script = "plugins {\n"
                    + "    id 'net.neoforged.moddev.legacyforge' version '2.0.146'\n"
                    + "}\n\n"
                    + autoConnectVariables()
                    + "dependencies {\n"
                    + "    modImplementation 'uk.co.enderfall.sdk:" + runtimeArtifact + ":"
                    + TargetCatalog.SDK_VERSION + "'\n"
                    + "}\n\n"
                    + "legacyForge {\n"
                    + legacyVersion
                    + "    validateAccessTransformers = true\n"
                    + "    runs {\n"
                    + modDevClientRun(!target.loader().equals("neoforge"))
                    + "        server { server(); gameDirectory = file('run/server'); programArgument '--nogui' }\n"
                    + "    }\n"
                    + "    mods {\n"
                    + "        enderfall_consumer {\n"
                    + "            sourceSet(sourceSets.main)\n"
                    + "            sourceSet(sourceSets.portable)\n"
                    + "        }\n"
                    + "    }\n"
                    + "}\n\n"
                    + "tasks.named('runServer').configure { standardInput = System.in }\n";
        } else if (target.loader().equals("neoforge")
                && (target.minecraftVersion().equals("1.21.1")
                || target.minecraftVersion().equals("1.21.4")
                || target.minecraftVersion().equals("26.2"))) {
            String neoVersion = target.loaderVersion();
            String runtimeArtifact = "enderfall-sdk-runtime-" + target.minecraftVersion() + "-neoforge";
            script = "plugins {\n"
                    + "    id 'net.neoforged.moddev' version '2.0.146'\n"
                    + "}\n\n"
                    + autoConnectVariables()
                    + "dependencies {\n"
                    + "    implementation 'uk.co.enderfall.sdk:" + runtimeArtifact + ":"
                    + TargetCatalog.SDK_VERSION + "'\n"
                    + "}\n\n"
                    + "neoForge {\n"
                    + "    version = '" + neoVersion + "'\n"
                    + "    runs {\n"
                    + modDevClientRun(true)
                    + "        server { server(); gameDirectory = file('run/server'); programArgument '--nogui' }\n"
                    + "    }\n"
                    + "    mods {\n"
                    + "        enderfall_consumer {\n"
                    + "            sourceSet(sourceSets.main)\n"
                    + "            sourceSet(sourceSets.portable)\n"
                    + "        }\n"
                    + "    }\n"
                    + "}\n\n"
                    + "tasks.named('runServer').configure { standardInput = System.in }\n";
        } else {
            script = "// EnderFall target build is configured by the settings plugin.\n";
        }
        try {
            Files.writeString(directory.toPath().resolve("build.gradle"), script,
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new GradleException("Cannot write generated target build for " + target.id(), exception);
        }
    }

    private static String autoConnectVariables() {
        return "def enderfallTestServerHost = System.getenv('ENDERFALL_TEST_SERVER_HOST') ?: '127.0.0.1'\n"
                + "def enderfallTestServerPort = System.getenv('ENDERFALL_TEST_SERVER_PORT')\n\n";
    }

    private static String fabricClientRun() {
        return "        client {\n"
                + "            runDir 'run/client'\n"
                + "            if (enderfallTestServerPort) {\n"
                + "                programArgs '--quickPlayMultiplayer', enderfallTestServerHost + ':' + enderfallTestServerPort\n"
                + "            }\n"
                + "        }\n";
    }

    private static String modDevClientRun(boolean quickPlayAutoConnect) {
        return "        client {\n"
                + "            client()\n"
                + "            gameDirectory = file('run/client')\n"
                + (quickPlayAutoConnect
                ? "            if (enderfallTestServerPort) {\n"
                + "                programArgument '--quickPlayMultiplayer'\n"
                + "                programArgument enderfallTestServerHost + ':' + enderfallTestServerPort\n"
                + "            }\n" : "")
                + "        }\n";
    }
}
