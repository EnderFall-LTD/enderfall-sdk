package uk.co.enderfall.sdk.gradle;

import java.io.File;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import uk.co.enderfall.sdk.gradle.model.LibraryDefinition;
import uk.co.enderfall.sdk.gradle.model.ModDefinition;
import uk.co.enderfall.sdk.gradle.model.TargetCatalog;
import uk.co.enderfall.sdk.gradle.task.VerifyPortableSourcesTask;

/** Configures the single Java 17 API artifact produced by a library-mod consumer. */
final class LibraryApiProjectConfigurator {
    private LibraryApiProjectConfigurator() {
    }

    static void configure(Project project, File consumerRoot, ModDefinition mod, LibraryDefinition library) {
        project.getPluginManager().apply(JavaLibraryPlugin.class);
        project.getPluginManager().apply(MavenPublishPlugin.class);
        project.setGroup(mod.getGroup());
        project.setVersion(mod.getVersion());
        project.setDescription(mod.getName() + " portable API");

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(17));
        java.withSourcesJar();
        java.withJavadocJar();

        SourceSet main = project.getExtensions().getByType(SourceSetContainer.class)
                .getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        File apiJava = new File(consumerRoot, "src/api/java");
        File apiResources = new File(consumerRoot, "src/api/resources");
        main.getJava().setSrcDirs(List.of(apiJava));
        main.getResources().setSrcDirs(List.of(apiResources));
        project.getDependencies().add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME,
                "uk.co.enderfall.sdk:enderfall-sdk-api:" + TargetCatalog.SDK_VERSION);

        var portableCheck = project.getTasks().register("verifyPortableApiSources",
                VerifyPortableSourcesTask.class, task -> {
                    task.setGroup("verification");
                    task.setDescription("Rejects Minecraft and loader references in the published library API.");
                    task.getSourceFiles().from(project.fileTree(apiJava, spec -> spec.include("**/*.java")));
                });
        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            task.getOptions().getRelease().set(17);
            task.getOptions().setEncoding("UTF-8");
            task.getOptions().getCompilerArgs().addAll(List.of("-Xlint:all", "-Werror"));
            task.dependsOn(portableCheck);
        });
        project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class, task -> {
            task.getArchiveBaseName().set(library.resolvedApiArtifact(mod));
            task.setPreserveFileTimestamps(false);
            task.setReproducibleFileOrder(true);
            File license = new File(consumerRoot, "LICENSE");
            if (license.isFile()) {
                task.from(license, spec -> spec.rename(ignored -> "LICENSE-" + mod.getId()));
            }
        });

        project.getExtensions().configure(org.gradle.api.publish.PublishingExtension.class, publishing -> {
            publishing.getRepositories().maven(repository -> {
                repository.setName("EnderfallLibraryWorkspace");
                repository.setUrl(new File(consumerRoot, "build/library-repository"));
            });
            if (!library.getRepositoryUrl().isBlank()) {
                publishing.getRepositories().maven(repository -> {
                    repository.setName("EnderfallLibraryRelease");
                    repository.setUrl(library.getRepositoryUrl());
                    repository.credentials(PasswordCredentials.class);
                });
            }
            publishing.getPublications().create("enderfallLibraryApi", MavenPublication.class, publication -> {
                publication.setGroupId(mod.getGroup());
                publication.setArtifactId(library.resolvedApiArtifact(mod));
                publication.setVersion(mod.getVersion());
                publication.from(project.getComponents().getByName("java"));
                publication.getPom().getName().set(mod.getName() + " API");
                publication.getPom().getDescription().set("Portable Java 17 API for " + mod.getName());
            });
        });
    }
}
