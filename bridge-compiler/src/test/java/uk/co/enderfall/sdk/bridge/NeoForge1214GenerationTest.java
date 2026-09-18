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
            "eb2a2df54b831d242b1a11419adc0f6d3bf7f78a3fceec0d6a59e3bedb08c647";
    private static final String PACKAGE_PATH =
            "uk/co/enderfall/sdk/runtime/neoforge/v1_21_4/";
    private static final Map<String, String> EXPECTED_SOURCE_HASHES = Map.ofEntries(
            Map.entry("EnderfallNeoForgeRuntime.java",
                    "62d1fa0bfef75dc0a99011a04a608be1a568321f0d1eb63be4000d8dccb1ef81"),
            Map.entry("NeoForgeClientHooks.java",
                    "d12f2ba485c19bf0549dc0f39b2d0629ba759604357743329aaaa43fa28b0378"),
            Map.entry("NeoForgeCommandBridge.java",
                    "814e75b21763a8814ab300c1afed2ed94fef1eecace7de828f8a3b5dbd042c97"),
            Map.entry("NeoForgeConsumerBootstrap.java",
                    "abf5473d5e2f3aed9f9472db8836e1bcac34e941784186c2f8c09ab291217b2a"),
            Map.entry("NeoForgePlatformAdapter.java",
                    "c7d4cf1069dce359588e8e903d2b2bcbbf813724a9d78778c12d6fa6bf5702b4"),
            Map.entry("NeoForgePlatformInfo.java",
                    "c565e0680ae053907f90d47df550ee817015f825fc9906bd2acd26b253b1c695"),
            Map.entry("NeoForgePortableMenuScreen.java",
                    "39085e39446d148180b1acabeffa4268024fd3058789533ff68c9d433a329746"),
            Map.entry("NeoForgeRawPayload.java",
                    "7c4c57af9e27a44dec9d8b370e1bc95bbf4ccd93e7a23547fcd39ef1d8c5001a"),
            Map.entry("NeoForgeRecipeBinding.java",
                    "0bc6f659cbfc715f99696da518cde274308de8fdad409805f344a6de001f3552"),
            Map.entry("NeoForgeWorkbenchBinding.java",
                    "527075791f8abeb4c6ccce12e39bfed1881e48b1d6aab3d8be90668cbc78e230"),
            Map.entry("NeoForgeWorkbenchInput.java",
                    "c600162ec228efae7ff15ae83e9b1d3878b4a077f01cbfea16a0d78100e9d177"),
            Map.entry("NeoForgeWorkbenchMenu.java",
                    "c48d7421d93a7042ea9058325151496ebfc4f0c469a5d09035a4c4b9a9f436fd"),
            Map.entry("NeoForgeWorkbenchRecipe.java",
                    "af80240af9c25aa54c70b26c9aaecf25a3a2a25e18e72b4627d0a24ece7c58f2"),
            Map.entry("NeoForgeWorkbenchScreen.java",
                    "cc2f9c426c4a5bf89c2f79d53e39f16c07d49b0832c7729834b92c33eaa6d97c"));

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
        assertEquals("24714146b6aabdc67a21a48890c8f2e5b3b94b261a3c00113e9afbe3074ccf73",
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
