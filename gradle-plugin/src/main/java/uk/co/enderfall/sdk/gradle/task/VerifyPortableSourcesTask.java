package uk.co.enderfall.sdk.gradle.task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Verification task has no outputs")
public abstract class VerifyPortableSourcesTask extends DefaultTask {
    private static final List<String> FORBIDDEN = List.of(
            "import net.minecraft.",
            "import net.fabricmc.",
            "import net.minecraftforge.",
            "import net.neoforged.",
            "//?",
            "/*?"
    );

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceFiles();

    @TaskAction
    public void verify() throws IOException {
        for (java.io.File file : getSourceFiles().getFiles()) {
            if (!file.isFile() || !file.getName().endsWith(".java")) {
                continue;
            }
            String source = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            for (String forbidden : FORBIDDEN) {
                if (source.contains(forbidden)) {
                    throw new GradleException("Portable source " + file + " contains forbidden token " + forbidden);
                }
            }
        }
    }
}
