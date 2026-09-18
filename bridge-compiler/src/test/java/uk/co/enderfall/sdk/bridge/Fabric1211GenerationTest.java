package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

class Fabric1211GenerationTest {
    private static final String PACKAGE_PATH = "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/";
    private static final String CANONICAL_MENU = PACKAGE_PATH + "FabricWorkbenchMenu.java";
    private static final String MENU_DESCRIPTION =
            "/** Real Fabric 1.21.4 container with vanilla slot synchronization. */";
    private static final Map<String, String> EXPECTED_SOURCE_HASHES = Map.ofEntries(
            Map.entry(PACKAGE_PATH + "EnderfallFabricRuntime.java",
                    "d373ffde76a07543b99d7da37e9ec3ab7da7d8d862eac426b23ebd0785cdb94e"),
            Map.entry(PACKAGE_PATH + "Fabric1211PlatformAdapter.java",
                    "bad4ce2c327ed64a5ed856a5fb0d8a4c680b95a3334d4aa44e44318116587440"),
            Map.entry(PACKAGE_PATH + "Fabric1211WorkbenchMenu.java",
                    "7a62b13e6482016d15e1b58655c8536fb3ac855021d3cf6190afef6c7a08f91a"),
            Map.entry(PACKAGE_PATH + "Fabric1211WorkbenchRecipe.java",
                    "d5e12ee40bd8c35226a1cf9eb3f49887f8e28e053fe7e86e40ad1b6b1a611798"),
            Map.entry(PACKAGE_PATH + "FabricClientHooks.java",
                    "b8e1694418bf577c8c71c1a8654d62f7cbfa7dc22ec40d55b6b2e67b2140404d"),
            Map.entry(PACKAGE_PATH + "FabricCommandBridge.java",
                    "079eee2af3cf9f61b2578ff626556a6cfb32a0dfbdf28ba15d31e69e583322f4"),
            Map.entry(PACKAGE_PATH + "FabricConsumerBootstrap.java",
                    "c3414e23f0805220fbeb7cde9baef4a96b564dab43dd050c7c0773d82722b0b5"),
            Map.entry(PACKAGE_PATH + "FabricPlatformInfo.java",
                    "097a350ff5517bc58ef656db4c606b516ef8631c8313a1a7674c7b40beaa0424"),
            Map.entry(PACKAGE_PATH + "FabricPortableMenuScreen.java",
                    "cbe7d1a0baa7838d351e8994e52877b77b8af25d68fd8b3a2202767ac20cf05c"),
            Map.entry(PACKAGE_PATH + "FabricRawPayload.java",
                    "45ed5bdacd32817664eb106aef40305c8846e791575131ced6be68b5458173a6"),
            Map.entry(PACKAGE_PATH + "FabricRecipeBinding.java",
                    "d3de31f065f05a043b3423ea99a1e23633e4376ff949aee2fd3d2157e733f913"),
            Map.entry(PACKAGE_PATH + "FabricWorkbenchBinding.java",
                    "371920f6adb8ceb8c8cae2094e6ab9fdd076fd453635d57ebaa34edf92a2b8ed"),
            Map.entry(PACKAGE_PATH + "FabricWorkbenchInput.java",
                    "139313be11f3853bd83ef47ec834280d015bc397d399cc34a3cb8396090ff5d8"),
            Map.entry(PACKAGE_PATH + "FabricWorkbenchScreen.java",
                    "e2cb7ae4e2e71911f268f7625887b304ce62e90276e4212195ab4bfec007d980"),
            Map.entry("uk/co/enderfall/sdk/runtime/item/nativebridge/PortableSdkItem.java",
                    "24714146b6aabdc67a21a48890c8f2e5b3b94b261a3c00113e9afbe3074ccf73"));
    private static final String EXPECTED_FABRIC_MOD_JSON = """
            {
              "schemaVersion": 1,
              "id": "enderfall_sdk",
              "version": "${version}",
              "name": "EnderFall SDK",
              "description": "Portable mod runtime for Minecraft 1.21.1 on Fabric",
              "authors": ["EnderFall"],
              "license": "Apache-2.0",
              "environment": "*",
              "entrypoints": {
                "main": [
                  "uk.co.enderfall.sdk.runtime.fabric.v1_21_4.EnderfallFabricRuntime"
                ]
              },
              "depends": {
                "fabricloader": ">=0.19.5",
                "fabric-api": ">=0.116.17+1.21.1",
                "minecraft": "=1.21.1",
                "java": ">=21"
              }
            }
            """;
    private static final String EXPECTED_PACK_METADATA = """
            {
              "pack": {
                "pack_format": 34,
                "supported_formats": [34, 48],
                "description": "EnderFall SDK runtime resources"
              }
            }
            """;

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesExactReviewedSourcesAndNormalizedResourcesDeterministically() throws Exception {
        Path canonical = canonicalRuntimeRoot();
        Path firstOutput = temporaryDirectory.resolve("first");
        Path secondOutput = temporaryDirectory.resolve("second");

        GenerationResult first = generate(canonical, firstOutput);
        GenerationResult second = generate(canonical, secondOutput);

        assertEquals(first, second);
        assertEquals("2b3e834a42e9963c9abd8e470f7b3373159b76bc62c13f1f2fd616da715cc217",
                first.sha256());
        assertEquals(17, first.files().size());
        assertEquals(new TreeMap<>(EXPECTED_SOURCE_HASHES), hashes(firstOutput.resolve("sources")));
        assertEquals(hashes(firstOutput.resolve("sources")), hashes(secondOutput.resolve("sources")));
        assertEquals(hashes(firstOutput.resolve("resources")), hashes(secondOutput.resolve("resources")));

        Path generatedPackage = firstOutput.resolve("sources").resolve(PACKAGE_PATH);
        assertFalse(Files.exists(generatedPackage.resolve("FabricPlatformAdapter.java")));
        assertFalse(Files.exists(generatedPackage.resolve("FabricWorkbenchMenu.java")));
        assertFalse(Files.exists(generatedPackage.resolve("FabricWorkbenchRecipe.java")));

        Path metadata = firstOutput.resolve("resources/fabric.mod.json");
        Path packMetadata = firstOutput.resolve("resources/pack.mcmeta");
        assertArrayEquals(EXPECTED_FABRIC_MOD_JSON.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(metadata));
        assertArrayEquals(EXPECTED_PACK_METADATA.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(packMetadata));
        assertEquals("90a3bf2b681f6192ff632ad44601ec0d92ba07a7696607ff6e636eb090577da4",
                sha256(Files.readAllBytes(metadata)));
        assertEquals("b6b5fd4e53f6393b66d5d10e176cd4f5fa0eff82e02d56350cd10e012d745c37",
                sha256(Files.readAllBytes(packMetadata)));
        assertNotEquals("87abe7a066fdaf9ce1ed943c95902b12a2e3c6b891da0ac8c9e6e274a1eae946",
                sha256(Files.readAllBytes(metadata)),
                "Normalized metadata intentionally differs from the old reference: it declares Fabric API");
    }

    @Test
    void reusesElevenCanonicalSourcesByteForByte() throws Exception {
        Path canonical = canonicalRuntimeRoot();
        Path output = temporaryDirectory.resolve("reuse");
        generate(canonical, output);

        List<String> transformedCanonicalNames = List.of("FabricClientHooks.java",
                "FabricPlatformAdapter.java", "FabricPortableMenuScreen.java",
                "FabricWorkbenchMenu.java", "FabricWorkbenchRecipe.java");
        int reused = 0;
        try (var stream = Files.walk(canonical.resolve("src/canonical/java"))) {
            for (Path canonicalFile : stream.filter(Files::isRegularFile).toList()) {
                if (transformedCanonicalNames.contains(canonicalFile.getFileName().toString())) {
                    continue;
                }
                Path relative = canonical.resolve("src/canonical/java").relativize(canonicalFile);
                assertArrayEquals(Files.readAllBytes(canonicalFile),
                        Files.readAllBytes(output.resolve("sources").resolve(relative)));
                reused++;
            }
        }
        assertEquals(9, reused);
    }

    @Test
    void rejectsChangedMenuManifestBeforeWriting() throws Exception {
        Path canonical = temporaryDirectory.resolve("missing-anchor-canonical");
        copyCanonicalRuntime(canonical);
        Path menu = canonical.resolve("src/canonical/java").resolve(CANONICAL_MENU);
        String source = Files.readString(menu, StandardCharsets.UTF_8);
        assertTrue(source.contains(MENU_DESCRIPTION));
        Files.writeString(menu, source.replace(MENU_DESCRIPTION, "/** deliberately drifted */"),
                StandardCharsets.UTF_8);
        Path output = temporaryDirectory.resolve("missing-anchor-output");

        BridgeGenerationException exception = assertThrows(BridgeGenerationException.class,
                () -> generate(canonical, output));

        assertTrue(exception.getMessage().contains("SHA-256 mismatch"));
        assertFalse(Files.exists(output));
    }

    @Test
    void rejectsAppendedMenuContentBeforeWriting() throws Exception {
        Path canonical = temporaryDirectory.resolve("duplicate-anchor-canonical");
        copyCanonicalRuntime(canonical);
        Path menu = canonical.resolve("src/canonical/java").resolve(CANONICAL_MENU);
        Files.writeString(menu,
                Files.readString(menu, StandardCharsets.UTF_8) + MENU_DESCRIPTION + '\n',
                StandardCharsets.UTF_8);
        Path output = temporaryDirectory.resolve("duplicate-anchor-output");

        BridgeGenerationException exception = assertThrows(BridgeGenerationException.class,
                () -> generate(canonical, output));

        assertTrue(exception.getMessage().contains("SHA-256 mismatch"));
        assertFalse(Files.exists(output));
    }

    @Test
    void reviewedMenuTextNoLongerControlsGeneratedOutput() throws Exception {
        Path canonical = temporaryDirectory.resolve("reviewed-menu-change");
        copyCanonicalRuntime(canonical);
        Path menu = canonical.resolve("src/canonical/java").resolve(CANONICAL_MENU);
        Files.writeString(menu, "// Feature declaration only; no Java template required.\n");
        writeManifest(canonical);
        assertEquals("2b3e834a42e9963c9abd8e470f7b3373159b76bc62c13f1f2fd616da715cc217",
                generate(canonical, temporaryDirectory.resolve("independent-output")).sha256());
    }

    @Test
    void rejectsAlteredReviewed1211TargetBeforeReadingCanonicalInput() {
        TargetSpec reviewed = TargetCatalog.standard().require("1.21.1-fabric");
        TargetSpec altered = new TargetSpec(
                reviewed.id(),
                reviewed.minecraftVersion(),
                reviewed.loader(),
                reviewed.javaVersion(),
                reviewed.loaderVersion(),
                "0.116.18+1.21.1",
                reviewed.loaderAbi(),
                reviewed.minecraftAbi(),
                reviewed.mappingAbi(),
                reviewed.recipeAbi(),
                reviewed.networkAbi(),
                reviewed.menuAbi());
        Path output = temporaryDirectory.resolve("altered-output");

        BridgeGenerationException exception = assertThrows(BridgeGenerationException.class,
                () -> new BridgeCompiler(TargetCatalog.of(List.of(altered))).generate(new GenerationRequest(
                        altered.id(), temporaryDirectory.resolve("missing-canonical"),
                        output.resolve("sources"), output.resolve("resources"))));

        assertTrue(exception.getMessage().contains("differs from its reviewed implemented specification"));
        assertFalse(Files.exists(output));
    }

    private static GenerationResult generate(Path canonical, Path output) throws BridgeGenerationException {
        return new BridgeCompiler().generate(new GenerationRequest(
                "1.21.1-fabric", canonical, output.resolve("sources"), output.resolve("resources")));
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
