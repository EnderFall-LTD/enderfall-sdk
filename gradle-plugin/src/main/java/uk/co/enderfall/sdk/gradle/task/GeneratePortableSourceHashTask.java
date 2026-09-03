package uk.co.enderfall.sdk.gradle.task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Emits a reproducible identity for the exact portable sources used by a target. */
@CacheableTask
public abstract class GeneratePortableSourceHashTask extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceFiles();

    @Internal
    public abstract DirectoryProperty getConsumerRoot();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void generate() throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Path root = getConsumerRoot().get().getAsFile().toPath();
            getSourceFiles().getFiles().stream()
                    .filter(java.io.File::isFile)
                    .sorted(Comparator.comparing(file -> root.relativize(file.toPath()).toString()
                            .replace('\\', '/')))
                    .forEach(file -> update(digest, root, file.toPath()));
            String hash = java.util.HexFormat.of().formatHex(digest.digest());
            Path output = getOutputFile().get().getAsFile().toPath();
            Files.createDirectories(output.getParent());
            Files.writeString(output, hash + '\n', StandardCharsets.US_ASCII);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, Path root, Path path) {
        try {
            String relative = root.relativize(path).toString().replace('\\', '/');
            digest.update(relative.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Files.readAllBytes(path));
            digest.update((byte) 0);
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }
}
