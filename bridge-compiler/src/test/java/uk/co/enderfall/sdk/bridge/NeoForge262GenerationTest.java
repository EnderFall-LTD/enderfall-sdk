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

class NeoForge262GenerationTest {
    private static final String TARGET = "26.2-neoforge";
    private static final String PACKAGE_PATH = "uk/co/enderfall/sdk/runtime/neoforge/v1_21_4/";

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatesReviewedSourcesAndResourcesDeterministically() throws Exception {
        Path firstOutput = temporaryDirectory.resolve("first");
        Path secondOutput = temporaryDirectory.resolve("second");
        GenerationResult first = generate(firstOutput);
        assertEquals(first, generate(secondOutput));
        assertEquals("c12e22e4e4808a8d792acfe5b393ad74c9b6fe62657aa2e3fba835a0bb68262f", first.sha256());
        assertEquals(15, first.files().size());
        assertEquals(hashes(firstOutput), hashes(secondOutput));

        // Independent hashes of the working reference, not expectations derived from emitted output.
        assertEquals("92e9644e7ccf77682ef4c435f44505d9e810161828f3f1b5d7e1656c1811e678",
                sha256(firstOutput.resolve("sources/" + PACKAGE_PATH + "NeoForge26PlatformAdapter.java")));
        assertEquals("759e5c25bfe5fc60442cde7e57a6367da067e270808f3f77066b287f6b2258fb",
                sha256(firstOutput.resolve("sources/uk/co/enderfall/sdk/runtime/item/nativebridge/PortableSdkItem.java")));
        assertEquals("f7903394415117ad9d43804e14b08d7ed22f9f0f6c12451680152dea4cd21ff6",
                sha256(firstOutput.resolve("sources/" + PACKAGE_PATH + "NeoForge26WorkbenchMenu.java")));
        assertEquals("f3f833abd1eb94e772093270eb22e651beb9ef6e7fdf79f61aa0e00991e2a5cb",
                sha256(firstOutput.resolve("sources/" + PACKAGE_PATH + "NeoForge26WorkbenchRecipe.java")));
        assertEquals("d2634532424a9ab9db67388c9931969ba2f293c56c3991d1bcea13b71419ca22",
                sha256(firstOutput.resolve("resources/META-INF/neoforge.mods.toml")));
        assertEquals("47f9bec07b63b124ca840aa7af74fc841c0f9329cd8b6e77d3598be56ab8336b",
                sha256(firstOutput.resolve("resources/pack.mcmeta")));
        String metadata = Files.readString(firstOutput.resolve("resources/META-INF/neoforge.mods.toml"));
        assertTrue(metadata.contains("loaderVersion=\"[10,)\""));
        assertTrue(metadata.contains("versionRange=\"[26.2.0.75,)\""));
        assertTrue(metadata.contains("versionRange=\"[26.2]\""));
    }

    @Test
    void reusesThreeSourcesAndOmitsEveryReplacedOrCombinedBaselineFile() throws Exception {
        Path output = temporaryDirectory.resolve("reuse");
        generate(output);
        List<String> reused = List.of("EnderfallNeoForgeRuntime.java", "NeoForgeConsumerBootstrap.java",
                "NeoForgeRawPayload.java");
        Path canonical = canonicalRoot().resolve("src/neoforge/java");
        int omitted = 0;
        try (var files = Files.walk(canonical)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path emitted = output.resolve("sources").resolve(canonical.relativize(file));
                if (reused.contains(file.getFileName().toString())) {
                    assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(emitted));
                } else {
                    assertFalse(Files.exists(emitted), file.getFileName().toString());
                    omitted++;
                }
            }
        }
        assertEquals(11, omitted);
    }

    @Test
    void refusesIncompletePlatformDeclarationsBeforeEmittingAnyResult() throws Exception {
        Map<String, byte[]> sources = new TreeMap<>();
        Path root = canonicalRoot().resolve("src/neoforge/java");
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                sources.put(root.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        }
        sources.remove(PACKAGE_PATH + "NeoForgeClientHooks.java");
        BridgeGenerationException failure = assertThrows(BridgeGenerationException.class,
                () -> PlatformServiceEmitter.emitIfPresent(TargetCatalog.standard().require(TARGET), sources.keySet()));
        assertTrue(failure.getMessage().contains("26.2-neoforge"));
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

    private static Map<String, String> hashes(Path root) throws Exception {
        Map<String, String> hashes = new TreeMap<>();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                hashes.put(root.relativize(file).toString(), sha256(file));
            }
        }
        return hashes;
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
