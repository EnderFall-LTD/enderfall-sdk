package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;

class Fabric1201GenerationTest {
    private static final String TARGET = "1.20.1-fabric";
    private static final String PACKAGE_PATH = "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/";

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesReviewedLegacySourcesAndResourcesDeterministically() throws Exception {
        Path firstOutput = temporaryDirectory.resolve("first");
        Path secondOutput = temporaryDirectory.resolve("second");
        GenerationResult first = generate(firstOutput);
        assertEquals(first, generate(secondOutput));
        assertEquals("0a623fad3f4ba234d3ce7a27f5fc6d2d61f11f12c19c1dd039532275517a680c", first.sha256());
        assertEquals(14, first.files().size());
        try (var files = Files.walk(firstOutput)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                assertArrayEquals(Files.readAllBytes(file),
                        Files.readAllBytes(secondOutput.resolve(firstOutput.relativize(file))));
            }
        }

        // Independent working-reference hashes lock the legacy transport and recipe/menu ABI.
        assertEquals("3329a727db068c81982619c6cbca65e37603b33eadfcb8a23571a372532a9840",
                sha256(firstOutput.resolve("sources/" + PACKAGE_PATH + "Fabric1201ClientHooks.java")));
        assertEquals("5919bd1c0d005b5983bceaef9d902abfa5eda65302468002d03ba64b931851be",
                sha256(firstOutput.resolve("sources/" + PACKAGE_PATH + "Fabric1201PlatformAdapter.java")));
        assertEquals("b734369f8d38a3ec4ddb160cf5eb053b320375e7981cf1bd102d31fae55bd212",
                sha256(firstOutput.resolve("sources/uk/co/enderfall/sdk/runtime/item/nativebridge/PortableSdkItem.java")));
        assertEquals("64bc53b937943f8aff0e92d54d3f45e42153e2115749cd2e57d331a2b0b184a4",
                sha256(firstOutput.resolve("sources/" + PACKAGE_PATH + "Fabric1201WorkbenchMenu.java")));
        assertEquals("8a3c6eb3e66c85cd2d566140e970928e85ec4774051874f6cd82c0f9be74ff15",
                sha256(firstOutput.resolve("sources/" + PACKAGE_PATH + "Fabric1201WorkbenchRecipe.java")));
        assertEquals("bbc6c144aa90ec88ebdb2ce541736ffe1f621a44ce2c13c240a1f0d673c960b4",
                sha256(firstOutput.resolve("resources/fabric.mod.json")));
        assertEquals("7b0dae32ae8a40fbe3b2bb408336896f8aefe41d4039b8b5745d7cdaf6046ff7",
                sha256(firstOutput.resolve("resources/pack.mcmeta")));
        String metadata = Files.readString(firstOutput.resolve("resources/fabric.mod.json"));
        assertTrue(metadata.contains("\"minecraft\": \"=1.20.1\""));
        assertTrue(metadata.contains("\"java\": \">=17\""));
    }

    @Test
    void reusesFourSourcesTransformsTheScreenAndOmitsNineReplacedOrCombinedFiles() throws Exception {
        Path output = temporaryDirectory.resolve("reuse");
        generate(output);
        List<String> reused = List.of("EnderfallFabricRuntime.java", "FabricCommandBridge.java",
                "FabricConsumerBootstrap.java", "FabricPlatformInfo.java");
        List<String> transformed = List.of("FabricPortableMenuScreen.java");
        Path canonical = canonicalRoot().resolve("src/canonical/java");
        int omitted = 0;
        try (var files = Files.walk(canonical)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path emitted = output.resolve("sources").resolve(canonical.relativize(file));
                if (reused.contains(file.getFileName().toString())) {
                    assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(emitted));
                } else if (transformed.contains(file.getFileName().toString())) {
                    assertTrue(Files.exists(emitted));
                    assertTrue(Files.mismatch(file, emitted) >= 0L);
                } else {
                    assertFalse(Files.exists(emitted), file.getFileName().toString());
                    omitted++;
                }
            }
        }
        assertEquals(9, omitted);
    }

    @Test
    void rejectsIncompleteLegacyPlatformDeclarations() throws Exception {
        Map<String, byte[]> sources = new TreeMap<>();
        Path root = canonicalRoot().resolve("src/canonical/java");
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                sources.put(root.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        }
        sources.remove(PACKAGE_PATH + "FabricClientHooks.java");
        BridgeGenerationException failure = assertThrows(BridgeGenerationException.class,
                () -> PlatformServiceEmitter.emitIfPresent(TargetCatalog.standard().require(TARGET), sources.keySet()));
        assertTrue(failure.getMessage().contains("1.20.1-fabric"));
        assertTrue(failure.getMessage().contains("requires platform declaration"));
    }

    private static GenerationResult generate(Path output) throws BridgeGenerationException {
        return new BridgeCompiler().generate(new GenerationRequest(TARGET, canonicalRoot(),
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

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
