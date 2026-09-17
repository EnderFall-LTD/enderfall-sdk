package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NeoForge1211GenerationTest {
    private static final String TARGET = "1.21.1-neoforge";
    private static final String EXPECTED_DIGEST =
            "1e57d889e4bce92c7ab896cebe85ee0a93892ba1484c92564bc6c9d7acfd2a55";
    private static final String PACKAGE_PATH = "uk/co/enderfall/sdk/runtime/neoforge/v1_21_4/";
    private static final String CANONICAL_MENU = PACKAGE_PATH + "NeoForgeWorkbenchMenu.java";
    private static final String MENU_DESCRIPTION =
            "/** Real NeoForge 1.21.4 container with vanilla slot synchronization. */";

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesExactReviewedSourcesAndResourcesDeterministically() throws Exception {
        Path firstOutput = temporaryDirectory.resolve("first");
        Path secondOutput = temporaryDirectory.resolve("second");

        GenerationResult first = generate(canonicalRuntimeRoot(), firstOutput);
        GenerationResult second = generate(canonicalRuntimeRoot(), secondOutput);

        assertEquals(first, second);
        assertEquals(EXPECTED_DIGEST, first.sha256());
        assertEquals(16, first.files().size());
        assertEquals("8eecba80ab530ca683dd9c9a4b765607d7d7ed7d4365dd242b1ac70f26746320",
                sha256(Files.readAllBytes(firstOutput.resolve("sources").resolve(PACKAGE_PATH)
                        .resolve("NeoForge1211WorkbenchMenu.java"))));
        assertEquals("7289427ba2367fd434576d6cf4f6e9ee01aa7d01ff57a4ca4dc95b0475344693",
                sha256(Files.readAllBytes(firstOutput.resolve("sources").resolve(PACKAGE_PATH)
                        .resolve("NeoForge1211WorkbenchRecipe.java"))));
        assertFalse(Files.exists(firstOutput.resolve("sources").resolve(PACKAGE_PATH)
                .resolve("NeoForgeWorkbenchMenu.java")));
        assertFalse(Files.exists(firstOutput.resolve("sources").resolve(PACKAGE_PATH)
                .resolve("NeoForgeWorkbenchRecipe.java")));

        Path modsToml = firstOutput.resolve("resources/META-INF/neoforge.mods.toml");
        Path packMetadata = firstOutput.resolve("resources/pack.mcmeta");
        assertEquals("e255fb319d950cc2057c576725162c02b8bba11790ce3c31f407c4a35856191f",
                sha256(Files.readAllBytes(modsToml)));
        assertEquals("b6b5fd4e53f6393b66d5d10e176cd4f5fa0eff82e02d56350cd10e012d745c37",
                sha256(Files.readAllBytes(packMetadata)));
        assertEquals(hashes(firstOutput.resolve("sources")), hashes(secondOutput.resolve("sources")));
        assertEquals(hashes(firstOutput.resolve("resources")), hashes(secondOutput.resolve("resources")));

        String metadata = Files.readString(modsToml);
        assertTrue(metadata.contains("versionRange=\"[21.1.249,)\""));
        assertTrue(metadata.contains("versionRange=\"[1.21.1]\""));
    }

    @Test
    void reusesTwelveCentralNeoForgeSourcesByteForByte() throws Exception {
        Path canonical = canonicalRuntimeRoot();
        Path output = temporaryDirectory.resolve("reuse");
        generate(canonical, output);

        int reused = 0;
        try (var stream = Files.walk(canonical.resolve("src/neoforge/java"))) {
            for (Path canonicalFile : stream.filter(Files::isRegularFile).toList()) {
                String name = canonicalFile.getFileName().toString();
                if (name.equals("NeoForgeWorkbenchMenu.java") || name.equals("NeoForgeWorkbenchRecipe.java")) {
                    continue;
                }
                Path relative = canonical.resolve("src/neoforge/java").relativize(canonicalFile);
                assertArrayEquals(Files.readAllBytes(canonicalFile),
                        Files.readAllBytes(output.resolve("sources").resolve(relative)));
                reused++;
            }
        }
        assertEquals(12, reused);
    }

    @Test
    void rejectsChangedMenuManifestBeforeWriting() throws Exception {
        Path canonical = temporaryDirectory.resolve("drifted-canonical");
        copyCanonicalRuntime(canonical);
        Path menu = canonical.resolve("src/neoforge/java").resolve(CANONICAL_MENU);
        String source = Files.readString(menu, StandardCharsets.UTF_8);
        assertTrue(source.contains(MENU_DESCRIPTION));
        Files.writeString(menu, source.replace(MENU_DESCRIPTION, "/** deliberately drifted */"),
                StandardCharsets.UTF_8);
        Path output = temporaryDirectory.resolve("drifted-output");

        BridgeGenerationException exception = assertThrows(BridgeGenerationException.class,
                () -> generate(canonical, output));

        assertTrue(exception.getMessage().contains("SHA-256 mismatch"));
        assertFalse(Files.exists(output));
    }

    @Test
    void reviewedMenuTextNoLongerControlsGeneratedOutput() throws Exception {
        Path canonical = temporaryDirectory.resolve("reviewed-menu-change");
        copyCanonicalRuntime(canonical);
        Path menu = canonical.resolve("src/neoforge/java").resolve(CANONICAL_MENU);
        Files.writeString(menu, "// Feature declaration only; no Java template required.\n");
        writeManifest(canonical);
        assertEquals(EXPECTED_DIGEST, generate(canonical, temporaryDirectory.resolve("independent-output")).sha256());
    }

    private static GenerationResult generate(Path canonical, Path output) throws BridgeGenerationException {
        return new BridgeCompiler().generate(new GenerationRequest(
                TARGET, canonical, output.resolve("sources"), output.resolve("resources")));
    }

    private static Path canonicalRuntimeRoot() {
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path candidate : List.of(
                workingDirectory.resolve("bridge-runtime"),
                workingDirectory.resolve("../bridge-runtime").normalize())) {
            if (Files.isRegularFile(candidate.resolve("MANIFEST.sha256"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not locate bridge-runtime from " + workingDirectory);
    }

    private static void copyCanonicalRuntime(Path destination) throws IOException {
        Path source = canonicalRuntimeRoot();
        try (var stream = Files.walk(source)) {
            for (Path input : stream.sorted().toList()) {
                Path output = destination.resolve(source.relativize(input));
                if (Files.isDirectory(input)) {
                    Files.createDirectories(output);
                } else {
                    Files.copy(input, output, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void writeManifest(Path root) throws IOException {
        List<String> lines = new ArrayList<>();
        try (var stream = Files.walk(root.resolve("src"))) {
            for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                lines.add(sha256(Files.readAllBytes(file)) + "  "
                        + root.relativize(file).toString().replace('\\', '/'));
            }
        }
        Files.writeString(root.resolve("MANIFEST.sha256"), String.join("\n", lines) + '\n',
                StandardCharsets.UTF_8);
    }

    private static Map<String, String> hashes(Path root) throws IOException {
        Map<String, String> hashes = new TreeMap<>();
        try (var stream = Files.walk(root)) {
            for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                hashes.put(root.relativize(file).toString().replace('\\', '/'),
                        sha256(Files.readAllBytes(file)));
            }
        }
        return hashes;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
