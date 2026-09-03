package uk.co.enderfall.sdk.gradle.task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Verification task has no outputs")
public abstract class CheckDuplicateResourcesTask extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getResourceRoots();

    @TaskAction
    public void verify() throws IOException {
        Map<String, Path> firstSeen = new HashMap<>();
        for (java.io.File rootFile : getResourceRoots().getFiles()) {
            Path root = rootFile.toPath();
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    Path previous = firstSeen.putIfAbsent(relative, path);
                    if (previous != null) {
                        throw new GradleException("Duplicate relative path " + relative + " in " + previous
                                + " and " + path);
                    }
                }
            }
        }
    }
}
