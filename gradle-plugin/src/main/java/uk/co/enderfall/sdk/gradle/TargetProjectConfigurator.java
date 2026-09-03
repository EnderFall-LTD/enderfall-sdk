package uk.co.enderfall.sdk.gradle;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.language.jvm.tasks.ProcessResources;
import uk.co.enderfall.sdk.gradle.model.ModDefinition;
import uk.co.enderfall.sdk.gradle.model.TargetCatalog;
import uk.co.enderfall.sdk.gradle.model.TargetDefinition;
import uk.co.enderfall.sdk.gradle.task.CheckDuplicateResourcesTask;
import uk.co.enderfall.sdk.gradle.task.GenerateBootstrapSourcesTask;
import uk.co.enderfall.sdk.gradle.task.GenerateModMetadataTask;
import uk.co.enderfall.sdk.gradle.task.GeneratePortableSourceHashTask;
import uk.co.enderfall.sdk.gradle.task.VerifyPortableSourcesTask;

final class TargetProjectConfigurator {
    private TargetProjectConfigurator() {
    }

    static void configure(Project project, File consumerRoot, ModDefinition mod, TargetDefinition target,
                          List<URI> dependencyRepositories) {
        project.getPluginManager().apply(JavaPlugin.class);
        dependencyRepositories.forEach(repositoryUrl -> project.getRepositories().maven(repository -> {
            repository.setUrl(repositoryUrl);
            String host = repositoryUrl.getHost();
            if (host != null && host.equalsIgnoreCase("maven.fabricmc.net")) {
                repository.content(content -> content.includeGroupByRegex("net\\.fabricmc(?:\\..*)?"));
            } else if (host != null && host.equalsIgnoreCase("maven.neoforged.net")) {
                repository.content(content -> {
                    content.includeGroupByRegex("net\\.neoforged(?:\\..*)?");
                    content.includeGroupByRegex("net\\.neoforged\\.fancymodloader(?:\\..*)?");
                    content.includeGroup("net.minecraftforge");
                });
            }
        }));
        project.setGroup(mod.getGroup());
        project.setVersion(mod.getVersion());
        project.setDescription(mod.getName() + " for " + target.id());

        JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        javaExtension.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(target.javaVersion()));
        JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);

        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet portable = sourceSets.create("portable");
        List<File> portableJavaRoots = portableJavaRoots(consumerRoot);
        List<File> portableResourceRoots = portableResourceRoots(consumerRoot);
        List<File> nativeJavaRoots = nativeJavaRoots(consumerRoot, target);
        List<File> nativeResourceRoots = nativeResourceRoots(consumerRoot, target);
        List<File> allResourceRoots = new ArrayList<>(portableResourceRoots);
        allResourceRoots.addAll(nativeResourceRoots);
        List<File> allJavaRoots = new ArrayList<>(portableJavaRoots);
        allJavaRoots.addAll(nativeJavaRoots);
        portable.getJava().setSrcDirs(portableJavaRoots);
        portable.getResources().setSrcDirs(portableResourceRoots);
        main.getJava().setSrcDirs(nativeJavaRoots);
        main.getResources().setSrcDirs(nativeResourceRoots);
        main.setCompileClasspath(main.getCompileClasspath().plus(portable.getOutput()));
        main.setRuntimeClasspath(main.getRuntimeClasspath().plus(portable.getOutput()));

        project.getDependencies().add(portable.getCompileOnlyConfigurationName(),
                "uk.co.enderfall.sdk:enderfall-sdk-api:" + TargetCatalog.SDK_VERSION);
        project.getDependencies().add(main.getCompileOnlyConfigurationName(),
                "uk.co.enderfall.sdk:enderfall-sdk-api:" + TargetCatalog.SDK_VERSION);
        Configuration dataRuntime = project.getConfigurations().create("enderfallDataRuntime");
        dataRuntime.setCanBeConsumed(false);
        dataRuntime.setCanBeResolved(true);
        project.getDependencies().add(dataRuntime.getName(),
                "uk.co.enderfall.sdk:enderfall-sdk-runtime-core:" + TargetCatalog.SDK_VERSION);

        if (TargetCatalog.hasRuntimeAdapter(target)) {
            var bootstrap = project.getTasks().register("generateEnderfallBootstrap",
                    GenerateBootstrapSourcesTask.class, task -> {
                    task.setGroup("enderfall sdk");
                    task.setDescription("Generates the loader bootstrap for " + target.id());
                    task.getModId().set(mod.getId());
                    task.getEntrypoint().set(mod.getEntrypoint());
                    task.getClientEntrypoint().set(mod.getClientEntrypoint());
                    task.getLoader().set(target.loader());
                    task.getMinecraftVersion().set(target.minecraftVersion());
                    task.getOutputDirectory().set(project.getLayout().getBuildDirectory()
                            .dir("generated/enderfallBootstrap"));
                    });
            main.getJava().srcDir(bootstrap.flatMap(GenerateBootstrapSourcesTask::getOutputDirectory));
        }

        var metadata = project.getTasks().register("generateModMetadata", GenerateModMetadataTask.class, task -> {
            task.setGroup("enderfall sdk");
            task.setDescription("Generates loader and EnderFall metadata for " + target.id());
            task.getModId().set(mod.getId());
            task.getModName().set(mod.getName());
            task.getModVersion().set(mod.getVersion());
            task.getAuthor().set(mod.getAuthor());
            task.getLicenseName().set(mod.getLicense());
            task.getEntrypoint().set(mod.getEntrypoint());
            task.getClientEntrypoint().set(mod.getClientEntrypoint());
            task.getMinecraftVersion().set(target.minecraftVersion());
            task.getLoader().set(target.loader());
            task.getLoaderVersion().set(target.loaderVersion());
            task.getJavaVersion().set(target.javaVersion());
            task.getSdkVersion().set(TargetCatalog.SDK_VERSION);
            task.getBootstrapEnabled().set(TargetCatalog.hasRuntimeAdapter(target));
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("generated/enderfallMetadata"));
        });
        main.getResources().srcDir(metadata.flatMap(GenerateModMetadataTask::getOutputDirectory));

        var portableCheck = project.getTasks().register("verifyPortableSources", VerifyPortableSourcesTask.class,
                task -> {
                    task.setGroup("verification");
                    task.setDescription("Rejects Minecraft, loader, and Stonecutter references in portable sources.");
                    portableJavaRoots.forEach(root -> task.getSourceFiles().from(project.fileTree(root,
                            spec -> spec.include("**/*.java"))));
                });

        var portableHash = project.getTasks().register("generatePortableSourceHash",
                GeneratePortableSourceHashTask.class, task -> {
                    task.setGroup("verification");
                    task.setDescription("Hashes the exact portable source set used by " + target.id());
                    portableJavaRoots.forEach(root -> task.getSourceFiles().from(project.fileTree(root,
                            spec -> spec.include("**/*.java"))));
                    task.getConsumerRoot().set(consumerRoot);
                    task.getOutputFile().set(project.getLayout().getBuildDirectory()
                            .file("generated/portableHash/META-INF/enderfall/portable-source.sha256"));
                });
        var portableHashRoot = project.getLayout().getBuildDirectory().dir("generated/portableHash");
        main.getResources().srcDir(portableHashRoot);

        var duplicateCheck = project.getTasks().register("checkDuplicateResources", CheckDuplicateResourcesTask.class,
                task -> {
                    task.setGroup("verification");
                    task.setDescription("Fails when multiple selected source roots contain the same resource path.");
                    task.getResourceRoots().from(allResourceRoots);
                    task.getResourceRoots().from(metadata.flatMap(GenerateModMetadataTask::getOutputDirectory));
                    task.getResourceRoots().from(portableHashRoot);
                    task.dependsOn(metadata, portableHash);
                });
        var duplicateClassCheck = project.getTasks().register("checkDuplicateClasses",
                CheckDuplicateResourcesTask.class, task -> {
                    task.setGroup("verification");
                    task.setDescription("Fails when multiple selected source roots contain the same Java class path.");
                    task.getResourceRoots().from(allJavaRoots);
                });

        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            int release = task.getName().equals(portable.getCompileJavaTaskName()) ? 17 : target.javaVersion();
            task.getOptions().getRelease().set(release);
            if (release == 17) {
                task.getJavaCompiler().set(toolchains.compilerFor(spec ->
                        spec.getLanguageVersion().set(JavaLanguageVersion.of(17))));
            }
            task.getOptions().setEncoding("UTF-8");
            task.getOptions().getCompilerArgs().addAll(List.of("-Xlint:all", "-Werror"));
            if (task.getName().equals(portable.getCompileJavaTaskName())) {
                task.dependsOn(portableCheck);
            }
        });
        project.getTasks().named(JavaPlugin.PROCESS_RESOURCES_TASK_NAME, ProcessResources.class, task -> {
            task.dependsOn(metadata, portableHash, duplicateCheck);
            task.setDuplicatesStrategy(DuplicatesStrategy.FAIL);
        });
        project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class, task -> {
            task.getArchiveFileName().set(artifactName(mod, target));
            task.from(portable.getOutput());
            task.dependsOn(duplicateCheck, duplicateClassCheck, portableHash);
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);
            File license = new File(consumerRoot, "LICENSE");
            if (license.isFile()) {
                task.from(license, spec -> spec.rename(ignored -> "LICENSE-" + mod.getId()));
            }
            task.getManifest().attributes(java.util.Map.of(
                    "Implementation-Title", mod.getName(),
                    "Implementation-Version", mod.getVersion(),
                    "EnderFall-Target", target.id(),
                    "EnderFall-Runtime-Status", "COMPILE_VALIDATED"
            ));
        });
        project.getTasks().withType(AbstractArchiveTask.class).configureEach(task -> {
            if (task.getName().equals("remapJar") || task.getName().equals("reobfJar")) {
                task.getArchiveFileName().set(artifactName(mod, target));
            }
        });
        project.getTasks().named("check").configure(task ->
                task.dependsOn(portableCheck, duplicateCheck, duplicateClassCheck));

        project.getTasks().register("collectArtifact", Copy.class, task -> {
            task.setGroup("build");
            task.dependsOn(project.getTasks().named("build"));
            task.from(project.provider(() -> {
                var reobf = project.getTasks().findByName("reobfJar");
                if (reobf instanceof AbstractArchiveTask archiveTask) {
                    return archiveTask.getArchiveFile().get().getAsFile();
                }
                var remap = project.getTasks().findByName("remapJar");
                if (remap instanceof AbstractArchiveTask archiveTask) {
                    return archiveTask.getArchiveFile().get().getAsFile();
                }
                return project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class)
                        .get().getArchiveFile().get().getAsFile();
            }));
            task.into(new File(consumerRoot, "build/releases"));
        });

        if (!TargetCatalog.hasRuntimeAdapter(target)) {
            registerUnavailableRuntimeTask(project, "runClient", target);
            registerUnavailableRuntimeTask(project, "runServer", target);
        }
        var dataOutput = project.getLayout().getBuildDirectory().dir("generated/enderfallData/resources");
        main.getResources().srcDir(dataOutput);
        var generateData = project.getTasks().register("generateData", JavaExec.class, task -> {
            task.setGroup("enderfall sdk");
            task.setDescription("Generates portable data for " + target.id());
            task.dependsOn(project.getTasks().named(portable.getClassesTaskName()));
            task.getMainClass().set("uk.co.enderfall.sdk.runtime.data.PortableDataGeneratorMain");
            task.setClasspath(project.files(portable.getOutput(), dataRuntime));
            task.getJavaLauncher().set(toolchains.launcherFor(spec ->
                    spec.getLanguageVersion().set(JavaLanguageVersion.of(17))));
            task.args(mod.getId(), mod.getEntrypoint(), target.minecraftVersion(), target.loader(),
                    dataOutput.get().getAsFile().getAbsolutePath());
            task.getOutputs().dir(dataOutput);
        });
        project.getTasks().named(JavaPlugin.PROCESS_RESOURCES_TASK_NAME).configure(task ->
                task.dependsOn(generateData));
    }

    private static void registerUnavailableRuntimeTask(Project project, String taskName, TargetDefinition target) {
        project.getTasks().register(taskName, task -> {
            task.setGroup("enderfall sdk");
            task.setDescription("Launches " + taskName.substring(3).toLowerCase() + " for " + target.id());
            task.doLast(ignored -> {
                throw new GradleException("Minecraft launch wiring for " + target.id()
                        + " is not available until that runtime adapter passes its contract build");
            });
        });
    }

    private static String artifactName(ModDefinition mod, TargetDefinition target) {
        return mod.getId() + '-' + mod.getVersion() + "+mc"
                + target.minecraftVersion() + '-' + target.loader() + ".jar";
    }

    private static List<File> portableJavaRoots(File root) {
        List<File> roots = new ArrayList<>();
        roots.add(new File(root, "src/main/java"));
        roots.add(new File(root, "src/client/java"));
        roots.add(new File(root, "src/datagen/java"));
        return roots;
    }

    private static List<File> nativeJavaRoots(File root, TargetDefinition target) {
        List<File> roots = new ArrayList<>();
        roots.add(new File(root, "src/loader/" + target.loader() + "/java"));
        roots.add(new File(root, "src/version/" + target.minecraftVersion() + "/java"));
        roots.add(new File(root, "src/target/" + target.id() + "/java"));
        return roots;
    }

    private static List<File> portableResourceRoots(File root) {
        List<File> roots = new ArrayList<>();
        roots.add(new File(root, "src/main/resources"));
        roots.add(new File(root, "src/client/resources"));
        roots.add(new File(root, "src/datagen/resources"));
        return roots;
    }

    private static List<File> nativeResourceRoots(File root, TargetDefinition target) {
        List<File> roots = new ArrayList<>();
        roots.add(new File(root, "src/loader/" + target.loader() + "/resources"));
        roots.add(new File(root, "src/version/" + target.minecraftVersion() + "/resources"));
        roots.add(new File(root, "src/target/" + target.id() + "/resources"));
        return roots;
    }
}
