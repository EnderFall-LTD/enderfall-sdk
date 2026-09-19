package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NeoForge1214GenerationTest {
    private static final String TARGET = "1.21.4-neoforge";
    private static final String EXPECTED_DIGEST =
            "7328ccd922ba9de61ea882632410ce05201cec53631ab93b658b7b2013642e25";
    private static final String PACKAGE_PATH =
            "uk/co/enderfall/sdk/runtime/neoforge/v1_21_4/";
    private static final Map<String, String> EXPECTED_SOURCE_HASHES = Map.ofEntries(
            Map.entry("EnderfallNeoForgeRuntime.java",
                    "62d1fa0bfef75dc0a99011a04a608be1a568321f0d1eb63be4000d8dccb1ef81"),
            Map.entry("NeoForgeClientHooks.java",
                    "3c4866fa320b31ba6962c758cd5890c27be1644c690c8fc718f413eb8ebdac5e"),
            Map.entry("NeoForgeCommandBridge.java",
                    "431001d9a2d64e5e5877cffd707cfe159c6ceea229356edd2d412f6c4c78941a"),
            Map.entry("NeoForgeConsumerBootstrap.java",
                    "abf5473d5e2f3aed9f9472db8836e1bcac34e941784186c2f8c09ab291217b2a"),
            Map.entry("NeoForgePlatformAdapter.java",
                    "03301de205a39605e0a1a2efb5e8d026afbd962f74b4e2ec1d9e6e55fba75534"),
            Map.entry("NeoForgePlatformInfo.java",
                    "c565e0680ae053907f90d47df550ee817015f825fc9906bd2acd26b253b1c695"),
            Map.entry("NeoForgePortableMenuScreen.java",
                    "552d434ab91d9dbac25f132eaa53737e9ad4cde36ce3e28ee9ff405138d5d708"),
            Map.entry("NeoForgeRawPayload.java",
                    "7c4c57af9e27a44dec9d8b370e1bc95bbf4ccd93e7a23547fcd39ef1d8c5001a"),
            Map.entry("NeoForgeRecipeBinding.java",
                    "0bc6f659cbfc715f99696da518cde274308de8fdad409805f344a6de001f3552"),
            Map.entry("NeoForgeWorkbenchBinding.java",
                    "527075791f8abeb4c6ccce12e39bfed1881e48b1d6aab3d8be90668cbc78e230"),
            Map.entry("NeoForgeWorkbenchInput.java",
                    "c600162ec228efae7ff15ae83e9b1d3878b4a077f01cbfea16a0d78100e9d177"),
            Map.entry("NeoForgeWorkbenchMenu.java",
                    "47404ab974902b5721833071d42caf413c9472b120ef8391561e999d987c433e"),
            Map.entry("NeoForgeWorkbenchRecipe.java",
                    "af80240af9c25aa54c70b26c9aaecf25a3a2a25e18e72b4627d0a24ece7c58f2"),
            Map.entry("NeoForgeWorkbenchScreen.java",
                    "196d11558d37d4441c283b2819c9c00dc936d395df515168ca871cdbf9c83680"));

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesLockedNeoForge1214ProfileAndMetadata() throws Exception {
        Path output = temporaryDirectory.resolve("generated");
        GenerationResult result = new BridgeCompiler().generate(new GenerationRequest(
                TARGET,
                canonicalRuntimeRoot(),
                output.resolve("sources"),
                output.resolve("resources")));

        assertEquals(17, result.files().size());
        assertEquals(EXPECTED_DIGEST, result.sha256());
        assertEquals(EXPECTED_SOURCE_HASHES.size() + 1, countFiles(output.resolve("sources")));
        for (Map.Entry<String, String> source : EXPECTED_SOURCE_HASHES.entrySet()) {
            assertEquals(source.getValue(), sha256(Files.readAllBytes(
                    output.resolve("sources").resolve(PACKAGE_PATH).resolve(source.getKey()))));
        }
        assertEquals("0ebf07e88d2c6f1c41da78b346d6ed55973a62b93c008f3f1f0d0d330879917f",
                sha256(Files.readAllBytes(output.resolve("sources/uk/co/enderfall/sdk/runtime/item/nativebridge/PortableSdkItem.java"))));

        Path modsToml = output.resolve("resources/META-INF/neoforge.mods.toml");
        Path packMetadata = output.resolve("resources/pack.mcmeta");
        assertEquals("658de6506f33c4fe117a3694246591a987c870a5ca3305c431f90aec202cbd13",
                sha256(Files.readAllBytes(modsToml)));
        assertEquals("380d9004da5646cca7515f7215d6d8f3d3c1d0c75711027ef0bd8399b9f4598e",
                sha256(Files.readAllBytes(packMetadata)));

        String metadata = Files.readString(modsToml);
        assertTrue(metadata.contains("modId=\"enderfall_sdk\""));
        assertTrue(metadata.contains("versionRange=\"[21.4.157,)\""));
        assertTrue(metadata.contains("versionRange=\"[1.21.4]\""));
    }

    private static long countFiles(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).count();
        }
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

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
