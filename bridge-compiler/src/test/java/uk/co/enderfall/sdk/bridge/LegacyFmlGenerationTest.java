package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.co.enderfall.sdk.bridge.model.ModRuntimeMetadata;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;

class LegacyFmlGenerationTest {
    private static final List<String> TARGETS = List.of("1.20.1-forge", "1.20.1-neoforge");
    private static final String PACKAGE_PATH = "uk/co/enderfall/sdk/runtime/forge/v1_20_1/";
    // Independently hashed working 1.20.1 reference sources, shared by both loaders.
    private static final Map<String, String> REFERENCE_JAVA_HASHES = Map.ofEntries(
            Map.entry("EnderfallForgeRuntime.java", "a4208d6dc644248b14f0cd1ee2102dba7c924217933d49e294641c3b04f66a31"),
            Map.entry("LegacyForgeClientHooks.java", "a1cad5ad80302e67129dcf29b519f08ab9b7cc45d364982958e035a5ee2859a9"),
            Map.entry("LegacyForgeCommandBridge.java", "a2c0a1b471efc62005537185fbc486bd3623e63e9db415dd2e67ae69d975f6a4"),
            Map.entry("LegacyForgeConsumerBootstrap.java", "c558ac1712ba96c4d54e0f78a425d3c1a135089a8f8797621e0292002c32431b"),
            Map.entry("LegacyForgePlatformAdapter.java", "74b126e4fc5de1e44177daa9aa159cca9137fdd9f0d334ee504d3a89984afe96"),
            Map.entry("LegacyForgePlatformInfo.java", "47718fe428c4c2782cd519424933cbac9ea85e98cf1bdc47d48e772db7024f42"),
            Map.entry("LegacyForgePortableMenuScreen.java", "c4218970a6796f3565dd86c92ea55ba9e52243db5793e570aa5edd01d9547e41"),
            Map.entry("LegacyForgeRecipeBinding.java", "6d53b08752ab48ca26e051c6bf8681f85c3ce87aefeebd3833535bdc301db66d"),
            Map.entry("LegacyForgeWorkbenchBinding.java", "9c3fbbf1af8524c2e84dab2ed3f68bbc221932ebbdb013dca58431c5b2d6ab87"),
            Map.entry("LegacyForgeWorkbenchMenu.java", "da72c67be0174599c00d46b3e1d864baa6f218c7e1b4d17ca34fe59e21635ecc"),
            Map.entry("LegacyForgeWorkbenchRecipe.java", "6cb660698058e6c4daebe012384b7704444f38477623ea36ce6c96653a21b588"),
            Map.entry("LegacyForgeWorkbenchScreen.java", "8ca423e1d12bd7e7d123f91c7db7392c47ed5ec23e0553c207058cb6b5b02ee0"));

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesBothLegacyTargetsDeterministicallyWithExactlyTheSameJava() throws Exception {
        for (String target : TARGETS) {
            Path first = temporaryDirectory.resolve(target);
            Path second = temporaryDirectory.resolve(target + "-repeat");
            GenerationResult result = generate(target, first);
            assertEquals(result, generate(target, second));
            assertEquals(target.equals("1.20.1-forge")
                    ? "cfd18f1b1d23b93eead8ca7ad7c191b3b07505d6ea2b811681807be9d5756602"
                    : "289c757215e34af208c3d89bbef66a857f468ca8f951561e30a2c0e20ff8f912", result.sha256());
            assertEquals(16, result.files().size());
            assertEquals(hashes(first), hashes(second));
            assertEquals(17, TargetCatalog.standard().require(target).javaVersion());
            Map<String, String> expected = new TreeMap<>();
            REFERENCE_JAVA_HASHES.forEach((name, hash) -> expected.put(PACKAGE_PATH + name, hash));
            expected.put("uk/co/enderfall/sdk/runtime/item/nativebridge/PortableSdkItem.java",
                    "3f3546195cd01221b0ecc1625cf52e1cb4f3b225049bde6f615e5d7355186fcc");
            assertEquals(expected, hashes(first.resolve("sources")));
            assertFalse(Files.exists(first.resolve("sources/uk/co/enderfall/sdk/runtime/neoforge")));
            assertFalse(Files.exists(first.resolve("resources/META-INF/neoforge.mods.toml")));
        }
        assertEquals(hashes(temporaryDirectory.resolve(TARGETS.get(0)).resolve("sources")),
                hashes(temporaryDirectory.resolve(TARGETS.get(1)).resolve("sources")));
    }

    @Test
    void emitsTheLegacyMetadataAndDistinctLoaderIdentityWithoutModernPayloadTypes() throws Exception {
        Map<String, String> descriptors = Map.of(
                "1.20.1-forge", "2876c62bc3de83fb67feba82be8eb876f75fe9da27bbe431e1b2cdaa68b0bc56",
                "1.20.1-neoforge", "158303af8d1261335a4879702f5750ed855d569489c0c76b193753cf4ae0ede7");
        for (String target : TARGETS) {
            Path output = temporaryDirectory.resolve(target);
            generate(target, output);
            Path resources = output.resolve("resources");
            assertEquals(descriptors.get(target), sha256(resources.resolve("META-INF/mods.toml")));
            assertEquals("7b0dae32ae8a40fbe3b2bb408336896f8aefe41d4039b8b5745d7cdaf6046ff7",
                    sha256(resources.resolve("pack.mcmeta")));
            assertEquals(TargetCatalog.standard().require(target).loader().id() + "\n",
                    Files.readString(resources.resolve("META-INF/enderfall-sdk-loader")));
            String metadata = Files.readString(resources.resolve("META-INF/mods.toml"));
            assertTrue(metadata.contains("modId=\"forge\""));
            assertTrue(metadata.contains("mandatory=true"));
            assertTrue(metadata.contains("versionRange=\"[1.20.1]\""));
            assertEquals(3, hashes(resources).size());
        }
    }

    @Test
    void legacyAutomaticConnectionWaitsForPongAndForgeDataNotAnIconCallback() throws Exception {
        for (String target : TARGETS) {
            Path output = temporaryDirectory.resolve(target);
            generate(target, output);
            String source = Files.readString(output.resolve("sources/" + PACKAGE_PATH + "LegacyForgeClientHooks.java"));
            assertTrue(source.contains("pinger.pingServer(server, () -> { });"));
            assertTrue(source.contains("pingStarted && !connectionStarted && server.ping >= 0L && server.forgeData != null"));
            assertFalse(source.contains("pingComplete"));
        }
    }

    @Test
    void rejectsIncompletePlatformDeclarationsAndWrongResourceLoaderAbi() throws Exception {
        Map<String, byte[]> sources = new TreeMap<>();
        Path root = canonicalRoot().resolve("src/neoforge/java");
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                sources.put(root.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        }
        String sourcePath = "uk/co/enderfall/sdk/runtime/neoforge/v1_21_4/NeoForgeClientHooks.java";
        sources.remove(sourcePath);
        for (String target : TARGETS) {
            BridgeGenerationException failure = assertThrows(BridgeGenerationException.class,
                    () -> PlatformServiceEmitter.emitIfPresent(TargetCatalog.standard().require(target), sources.keySet()));
            assertTrue(failure.getMessage().contains(target));
            assertTrue(failure.getMessage().contains("requires platform declaration"));
        }
        var modern = TargetCatalog.standard().require("1.21.4-neoforge");
        assertThrows(BridgeGenerationException.class, () -> PlatformServiceEmitter.emitIfPresent(modern, sources.keySet()));
        assertThrows(BridgeGenerationException.class,
                () -> LegacyFmlResourceEmitter.emit(modern, ModRuntimeMetadata.enderfallSdk()));
    }

    @Test
    void escapesQuotesBackslashesAndControlCharactersInMetadata() throws Exception {
        var standard = ModRuntimeMetadata.enderfallSdk();
        String special = "A\"\\\n\t" + (char) 1 + (char) 127;
        var metadata = new ModRuntimeMetadata(standard.id(), standard.versionPlaceholder(), special,
                List.of(special), standard.license(), standard.environment(),
                standard.fabricMainEntrypoint(), special);
        String escaped = "A\\\"\\\\\\n\\t\\u0001\\u007f";
        for (RuntimeResource resource : LegacyFmlResourceEmitter.emit(
                TargetCatalog.standard().require("1.20.1-forge"), metadata)) {
            if (resource.relativePath().equals("META-INF/enderfall-sdk-loader")) {
                continue;
            }
            assertTrue(resource.content().contains(escaped), resource.relativePath());
            assertFalse(resource.content().contains(special));
        }
    }

    private static GenerationResult generate(String target, Path output) throws BridgeGenerationException {
        return new BridgeCompiler().generate(new GenerationRequest(target, canonicalRoot(),
                output.resolve("sources"), output.resolve("resources")));
    }

    private static Path canonicalRoot() {
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path candidate : List.of(working.resolve("bridge-runtime"), working.resolve("../bridge-runtime"))) {
            if (Files.isRegularFile(candidate.resolve("MANIFEST.sha256"))) {
                return candidate.normalize();
            }
        }
        throw new IllegalStateException("Cannot locate bridge-runtime from " + working);
    }

    private static Map<String, String> hashes(Path root) throws Exception {
        Map<String, String> hashes = new TreeMap<>();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                hashes.put(root.relativize(file).toString().replace('\\', '/'), sha256(file));
            }
        }
        return hashes;
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
