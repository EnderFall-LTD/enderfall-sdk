package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;

class DeclarationOnlyGenerationTest {
    @TempDir Path temporary;

    @Test void everyTargetHasIdenticalOutputWithoutAnyCanonicalJavaImplementation() throws Exception {
        Path original = canonicalRoot();
        Path declarations = temporary.resolve("declarations");
        List<String> manifest = new ArrayList<>();
        try (var files = Files.walk(original.resolve("src"))) {
            for (Path source : files.filter(Files::isRegularFile).sorted().toList()) {
                Path relative = original.relativize(source);
                Path destination = declarations.resolve(relative);
                Files.createDirectories(destination.getParent());
                byte[] bytes = source.toString().endsWith(".java")
                        ? ("// Reviewed feature declaration: " + source.getFileName() + "\n").getBytes(StandardCharsets.UTF_8)
                        : Files.readAllBytes(source);
                Files.write(destination, bytes);
                manifest.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
                        + "  " + relative.toString().replace('\\', '/'));
            }
        }
        Files.writeString(declarations.resolve("MANIFEST.sha256"), String.join("\n", manifest) + "\n", StandardCharsets.UTF_8);
        for (String target : TargetCatalog.standard().targetIds()) {
            Path baseline = temporary.resolve("baseline-" + target);
            Path emitted = temporary.resolve("emitted-" + target);
            var before = new BridgeCompiler().generate(new GenerationRequest(target, original,
                    baseline.resolve("sources"), baseline.resolve("resources")));
            var after = new BridgeCompiler().generate(new GenerationRequest(target, declarations,
                    emitted.resolve("sources"), emitted.resolve("resources")));
            assertEquals(before.sha256(), after.sha256(), target);
            assertEquals(before.files().size(), after.files().size(), target);
        }
    }

    private static Path canonicalRoot() {
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path candidate : List.of(working.resolve("bridge-runtime"), working.resolve("../bridge-runtime")))
            if (Files.isRegularFile(candidate.resolve("MANIFEST.sha256"))) return candidate.normalize();
        throw new IllegalStateException("Cannot locate bridge-runtime from " + working);
    }
}
