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

class Fabric262GenerationTest {
    private static final String PACKAGE_PATH = "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/";
    private static final String CANONICAL_PLATFORM = PACKAGE_PATH + "FabricPlatformAdapter.java";
    private static final String PLATFORM_IMPORT = "import net.fabricmc.api.EnvType;";
    private static final Map<String, String> EXPECTED_SOURCE_HASHES = Map.ofEntries(
            Map.entry(PACKAGE_PATH + "EnderfallFabricRuntime.java",
                    "d373ffde76a07543b99d7da37e9ec3ab7da7d8d862eac426b23ebd0785cdb94e"),
            Map.entry(PACKAGE_PATH + "FabricClientHooks.java",
                    "b8e1694418bf577c8c71c1a8654d62f7cbfa7dc22ec40d55b6b2e67b2140404d"),
            Map.entry(PACKAGE_PATH + "Fabric26CommandBridge.java",
                    "60e8dca1cd62b5d47fd20bab7a00d36d1829e7dab7a23dda010b6e26b9f1f529"),
            Map.entry(PACKAGE_PATH + "FabricConsumerBootstrap.java",
                    "c3414e23f0805220fbeb7cde9baef4a96b564dab43dd050c7c0773d82722b0b5"),
            Map.entry(PACKAGE_PATH + "Fabric26PlatformAdapter.java",
                    "67807c27ffe2b3838dd289ed6a28a4e1e1d51593c1b26d21e878b3176e871448"),
            Map.entry(PACKAGE_PATH + "FabricPlatformInfo.java",
                    "097a350ff5517bc58ef656db4c606b516ef8631c8313a1a7674c7b40beaa0424"),
            Map.entry(PACKAGE_PATH + "Fabric26PortableMenuScreen.java",
                    "6df8bb7b60c2f50d3ae098250887df4a731ba4d94c6416a3cff4ba3ccc785195"),
            Map.entry(PACKAGE_PATH + "FabricRawPayload.java",
                    "45ed5bdacd32817664eb106aef40305c8846e791575131ced6be68b5458173a6"),
            Map.entry(PACKAGE_PATH + "FabricRecipeBinding.java",
                    "d3de31f065f05a043b3423ea99a1e23633e4376ff949aee2fd3d2157e733f913"),
            Map.entry(PACKAGE_PATH + "FabricWorkbenchBinding.java",
                    "371920f6adb8ceb8c8cae2094e6ab9fdd076fd453635d57ebaa34edf92a2b8ed"),
            Map.entry(PACKAGE_PATH + "FabricWorkbenchInput.java",
                    "139313be11f3853bd83ef47ec834280d015bc397d399cc34a3cb8396090ff5d8"),
            Map.entry(PACKAGE_PATH + "Fabric26WorkbenchMenu.java",
                    "3d2ba1a3daec769c49373079feca8e7a15ebe45f9dcc71797e2303738e7e1a5c"),
            Map.entry(PACKAGE_PATH + "Fabric26WorkbenchRecipe.java",
                    "84b9fd197e7f5faa3c82c437953aeb7f716516b2f2bf47316b714d5e5a9ce1a2"),
            Map.entry(PACKAGE_PATH + "Fabric26WorkbenchScreen.java",
                    "712c4f399295243e6a3548465a21488670d591407b0887e4a97f7783be90211f"),
            Map.entry("uk/co/enderfall/sdk/runtime/item/nativebridge/PortableSdkItem.java",
                    "a0f24be0c2aa02e88c616cc0c77d1f837d4faa015d9607e1df40e932f6954610"));
    private static final String EXPECTED_PACK_METADATA = """
            {
              "pack": {
                "min_format": 88,
                "max_format": [107, 1],
                "description": "EnderFall SDK runtime resources"
              }
            }
            """;

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesExactReviewedSourcesAndResourcesDeterministically() throws Exception {
        Path canonical = canonicalRuntimeRoot();
        Path firstOutput = temporaryDirectory.resolve("first");
        Path secondOutput = temporaryDirectory.resolve("second");

        GenerationResult first = generate(canonical, firstOutput);
        GenerationResult second = generate(canonical, secondOutput);

        assertEquals(first, second);
        assertEquals("73b414c8198a417367e678bac78313e788fda7c2529985bfdf6f4ee3b3f478ce",
                first.sha256());
        assertEquals(17, first.files().size());
        assertEquals(new TreeMap<>(EXPECTED_SOURCE_HASHES), hashes(firstOutput.resolve("sources")));
        assertEquals(hashes(firstOutput.resolve("sources")), hashes(secondOutput.resolve("sources")));
        assertEquals(hashes(firstOutput.resolve("resources")), hashes(secondOutput.resolve("resources")));
        assertEquals("5f8acb19c474de49cd9f5648aa572e01ddd8bf5d3b14bae78df5bd153c99e7dc",
                sha256(Files.readAllBytes(firstOutput.resolve("resources/fabric.mod.json"))));
        assertEquals("47f9bec07b63b124ca840aa7af74fc841c0f9329cd8b6e77d3598be56ab8336b",
                sha256(Files.readAllBytes(firstOutput.resolve("resources/pack.mcmeta"))));
        assertArrayEquals(EXPECTED_PACK_METADATA.getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(firstOutput.resolve("resources/pack.mcmeta")));
    }

    @Test
    void reusesSevenCanonicalSourcesAndReplacesGeneratedNativeShells() throws Exception {
        Path canonical = canonicalRuntimeRoot();
        Path output = temporaryDirectory.resolve("reuse");
        generate(canonical, output);

        Map<String, String> replacements = Map.of(
                "FabricCommandBridge.java", "Fabric26CommandBridge.java",
                "FabricPlatformAdapter.java", "Fabric26PlatformAdapter.java",
                "FabricPortableMenuScreen.java", "Fabric26PortableMenuScreen.java",
                "FabricWorkbenchMenu.java", "Fabric26WorkbenchMenu.java",
                "FabricWorkbenchRecipe.java", "Fabric26WorkbenchRecipe.java",
                "FabricWorkbenchScreen.java", "Fabric26WorkbenchScreen.java");
        int reused = 0;
        Path canonicalJava = canonical.resolve("src/canonical/java");
        try (var stream = Files.walk(canonicalJava)) {
            for (Path canonicalFile : stream.filter(Files::isRegularFile).toList()) {
                String name = canonicalFile.getFileName().toString();
                Path relative = canonicalJava.relativize(canonicalFile);
                if (name.equals("FabricClientHooks.java")) {
                    assertTrue(Files.isRegularFile(output.resolve("sources").resolve(relative)));
                    assertFalse(java.util.Arrays.equals(Files.readAllBytes(canonicalFile),
                            Files.readAllBytes(output.resolve("sources").resolve(relative))));
                    continue;
                }
                if (replacements.containsKey(name)) {
                    assertFalse(Files.exists(output.resolve("sources").resolve(relative)));
                    assertTrue(Files.isRegularFile(output.resolve("sources").resolve(PACKAGE_PATH)
                            .resolve(replacements.get(name))));
                    continue;
                }
                assertArrayEquals(Files.readAllBytes(canonicalFile),
                        Files.readAllBytes(output.resolve("sources").resolve(relative)));
                reused++;
            }
        }
        assertEquals(7, reused);
    }

    @Test
    void rejectsUnreviewedPlatformDeclarationDriftBeforeWriting() throws Exception {
        Path canonical = temporaryDirectory.resolve("drifted-canonical");
        copyCanonicalRuntime(canonical);
        Path command = canonical.resolve("src/canonical/java").resolve(CANONICAL_PLATFORM);
        String source = Files.readString(command, StandardCharsets.UTF_8);
        assertTrue(source.contains(PLATFORM_IMPORT));
        Files.writeString(command, source.replace(PLATFORM_IMPORT,
                "import net.fabricmc.api.EnvType; // drifted"), StandardCharsets.UTF_8);
        Path output = temporaryDirectory.resolve("drifted-output");

        BridgeGenerationException exception = assertThrows(BridgeGenerationException.class,
                () -> generate(canonical, output));

        assertTrue(exception.getMessage().contains("Canonical manifest validation failed"));
        assertTrue(exception.getMessage().contains("SHA-256 mismatch"));
        assertFalse(Files.exists(output));
    }

    private static GenerationResult generate(Path canonical, Path output) throws BridgeGenerationException {
        return new BridgeCompiler().generate(new GenerationRequest(
                "26.2-fabric", canonical, output.resolve("sources"), output.resolve("resources")));
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

    private static Map<String, String> hashes(Path root) throws IOException {
        Map<String, String> hashes = new TreeMap<>();
        try (var stream = Files.walk(root)) {
            for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                hashes.put(root.relativize(file).toString().replace('\\', '/'), sha256(Files.readAllBytes(file)));
            }
        }
        return hashes;
    }

    private static void writeManifest(Path root) throws IOException {
        Path canonical = root.resolve("src");
        List<String> lines = new ArrayList<>();
        try (var stream = Files.walk(canonical)) {
            for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                Path relative = root.relativize(file);
                lines.add(sha256(Files.readAllBytes(file)) + "  "
                        + relative.toString().replace('\\', '/'));
            }
        }
        Files.writeString(root.resolve("MANIFEST.sha256"), String.join("\n", lines) + '\n',
                StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
