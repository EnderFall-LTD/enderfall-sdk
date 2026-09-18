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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

class BridgeCompilerTest {
    private static final String EXPECTED_FABRIC_MOD_JSON = """
            {
              "schemaVersion": 1,
              "id": "enderfall_sdk",
              "version": "${version}",
              "name": "EnderFall SDK",
              "description": "Portable mod runtime for Minecraft 1.21.4 on Fabric",
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
                "fabric-api": ">=0.119.4+1.21.4",
                "minecraft": "=1.21.4",
                "java": ">=21"
              }
            }
            """;
    private static final String EXPECTED_PACK_METADATA = """
            {
              "pack": {
                "pack_format": 46,
                "supported_formats": [46, 61],
                "description": "EnderFall SDK runtime resources"
              }
            }
            """;

    @TempDir
    Path temporaryDirectory;

    @Test
    void implementsEveryReviewedCatalogTarget() {
        assertEquals(uk.co.enderfall.sdk.bridge.model.TargetCatalog.standard().targetIds(),
                new BridgeCompiler().implementedTargetIds());
    }

    @Test
    void producesIdenticalFilesAndDigestRegardlessOfFilesystemCreationOrder() throws Exception {
        Path firstCanonical = temporaryDirectory.resolve("canonical-one");
        Path secondCanonical = temporaryDirectory.resolve("canonical-two");
        createCanonicalTree(firstCanonical, false);
        createCanonicalTree(secondCanonical, true);

        GenerationResult first = generate(firstCanonical, temporaryDirectory.resolve("first-output"));
        GenerationResult second = generate(secondCanonical, temporaryDirectory.resolve("second-output"));

        assertEquals(first.files(), second.files());
        assertEquals(first.sha256(), second.sha256());
        assertEquals(List.of(
                new GeneratedFile(OutputKind.RESOURCE, "fabric.mod.json", 508),
                new GeneratedFile(OutputKind.RESOURCE, "nested/data.txt", 5),
                new GeneratedFile(OutputKind.RESOURCE, "pack.mcmeta", 131),
                new GeneratedFile(OutputKind.SOURCE,
                        "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/EnderfallFabricRuntime.java", 487),
                new GeneratedFile(OutputKind.SOURCE,
                        "uk/co/enderfall/sdk/runtime/item/nativebridge/PortableSdkItem.java", 6652)), first.files());
        assertTreesEqual(
                temporaryDirectory.resolve("first-output/sources"),
                temporaryDirectory.resolve("second-output/sources"));
        assertTreesEqual(
                temporaryDirectory.resolve("first-output/resources"),
                temporaryDirectory.resolve("second-output/resources"));
    }

    @Test
    void rendersExactRuntimeResourcesFromTypedMetadataInsteadOfCopyingFixtures() throws Exception {
        Path canonical = temporaryDirectory.resolve("canonical");
        createCanonicalTree(canonical, false);
        Path output = temporaryDirectory.resolve("typed-output");

        generate(canonical, output);

        byte[] fabricModJson = Files.readAllBytes(output.resolve("resources/fabric.mod.json"));
        byte[] packMetadata = Files.readAllBytes(output.resolve("resources/pack.mcmeta"));
        assertArrayEquals(EXPECTED_FABRIC_MOD_JSON.getBytes(StandardCharsets.UTF_8), fabricModJson);
        assertArrayEquals(EXPECTED_PACK_METADATA.getBytes(StandardCharsets.UTF_8), packMetadata);
        assertEquals("db85224b2a90895281de3247722e85529dde547bebd4cd4e2976624f9ad827d0",
                sha256(fabricModJson));
        assertEquals("380d9004da5646cca7515f7215d6d8f3d3c1d0c75711027ef0bd8399b9f4598e",
                sha256(packMetadata));
        assertFalse(Files.readString(canonical.resolve("src/canonical/resources/fabric.mod.json"))
                .equals(Files.readString(output.resolve("resources/fabric.mod.json"))));
        assertFalse(Files.readString(canonical.resolve("src/canonical/resources/pack.mcmeta"))
                .equals(Files.readString(output.resolve("resources/pack.mcmeta"))));
    }

    @Test
    void rejectsAlteredMetadataForAnImplementedTargetId() {
        TargetSpec reviewed = TargetCatalog.standard().require("1.21.4-fabric");
        TargetSpec altered = new TargetSpec(
                reviewed.id(),
                reviewed.minecraftVersion(),
                reviewed.loader(),
                reviewed.javaVersion(),
                reviewed.loaderVersion(),
                "0.119.5+1.21.4",
                reviewed.loaderAbi(),
                reviewed.minecraftAbi(),
                reviewed.mappingAbi(),
                reviewed.recipeAbi(),
                reviewed.networkAbi(),
                reviewed.menuAbi());
        Path output = temporaryDirectory.resolve("altered-output");

        BridgeGenerationException exception = assertThrows(BridgeGenerationException.class,
                () -> new BridgeCompiler(TargetCatalog.of(List.of(altered))).generate(new GenerationRequest(
                        altered.id(), temporaryDirectory.resolve("unused"),
                        output.resolve("sources"), output.resolve("resources"))));

        assertTrue(exception.getMessage().contains("differs from its reviewed implemented specification"));
        assertFalse(Files.exists(output));
    }

    @Test
    void rejectsTraversalShapedTargetBeforeTouchingFilesystem() {
        Path outside = temporaryDirectory.resolve("outside");

        BridgeGenerationException exception = assertThrows(BridgeGenerationException.class,
                () -> new BridgeCompiler().generate(new GenerationRequest(
                        "../1.21.4-fabric",
                        temporaryDirectory.resolve("missing"),
                        outside.resolve("sources"),
                        outside.resolve("resources"))));

        assertTrue(exception.getMessage().contains("Unsafe or malformed target ID"));
        assertFalse(Files.exists(outside));
    }

    @Test
    void rejectsOutputNestedInsideCanonicalInput() throws Exception {
        Path canonical = temporaryDirectory.resolve("canonical");
        createCanonicalTree(canonical, false);

        BridgeGenerationException exception = assertThrows(BridgeGenerationException.class,
                () -> new BridgeCompiler().generate(new GenerationRequest(
                        "1.21.4-fabric",
                        canonical,
                        canonical.resolve("src/canonical/java/generated"),
                        temporaryDirectory.resolve("safe-resources"))));

        assertTrue(exception.getMessage().contains("must not be the same or nested paths"));
        assertFalse(Files.exists(temporaryDirectory.resolve("safe-resources")));
    }

    @Test
    void rejectsConflictingOutputDuringPreflightWithoutWritingMissingFiles() throws Exception {
        Path canonical = temporaryDirectory.resolve("canonical");
        createCanonicalTree(canonical, false);
        Path outputs = temporaryDirectory.resolve("outputs");
        Path collision = outputs.resolve(
                "sources/uk/co/enderfall/sdk/runtime/fabric/v1_21_4/EnderfallFabricRuntime.java");
        Files.createDirectories(collision.getParent());
        Files.writeString(collision, "user-owned", StandardCharsets.UTF_8);

        BridgeGenerationException exception = assertThrows(BridgeGenerationException.class,
                () -> generate(canonical, outputs));

        assertTrue(exception.getMessage().contains("differs from the deterministic plan"));
        assertEquals("user-owned", Files.readString(collision, StandardCharsets.UTF_8));
        assertFalse(Files.exists(outputs.resolve("resources/fabric.mod.json")));
    }

    @Test
    void identicalRerunIsIdempotentAndDoesNotRewriteOutputs() throws Exception {
        Path canonical = temporaryDirectory.resolve("canonical");
        createCanonicalTree(canonical, false);
        Path outputs = temporaryDirectory.resolve("idempotent-output");

        GenerationResult first = generate(canonical, outputs);
        Path generatedMetadata = outputs.resolve("resources/fabric.mod.json");
        var originalModifiedTime = Files.getLastModifiedTime(generatedMetadata);
        GenerationResult second = generate(canonical, outputs);

        assertEquals(first, second);
        assertEquals(originalModifiedTime, Files.getLastModifiedTime(generatedMetadata));
        assertArrayEquals(EXPECTED_FABRIC_MOD_JSON.getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(generatedMetadata));
        assertNoTemporaryFiles(outputs);
    }

    @Test
    void concurrentIdenticalGeneratorsAcceptTheExactWinningFiles() throws Exception {
        Path canonical = temporaryDirectory.resolve("canonical");
        createCanonicalTree(canonical, false);
        Path outputs = temporaryDirectory.resolve("concurrent-output");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<GenerationResult> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return generate(canonical, outputs);
            });
            Future<GenerationResult> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return generate(canonical, outputs);
            });
            ready.await();
            start.countDown();

            GenerationResult firstResult = first.get();
            GenerationResult secondResult = second.get();

            assertEquals(firstResult, secondResult);
            assertArrayEquals(EXPECTED_FABRIC_MOD_JSON.getBytes(StandardCharsets.UTF_8),
                    Files.readAllBytes(outputs.resolve("resources/fabric.mod.json")));
            assertArrayEquals(EXPECTED_PACK_METADATA.getBytes(StandardCharsets.UTF_8),
                    Files.readAllBytes(outputs.resolve("resources/pack.mcmeta")));
            assertNoTemporaryFiles(outputs);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsSharedSourceAndResourceOutputRoots() throws Exception {
        Path canonical = temporaryDirectory.resolve("canonical");
        createCanonicalTree(canonical, false);
        Path sharedOutput = temporaryDirectory.resolve("shared-output");

        BridgeGenerationException exception = assertThrows(BridgeGenerationException.class,
                () -> new BridgeCompiler().generate(new GenerationRequest(
                        "1.21.4-fabric", canonical, sharedOutput, sharedOutput)));

        assertTrue(exception.getMessage().contains("source output and resource output"));
        assertFalse(Files.exists(sharedOutput));
    }

    @Test
    void permitsUnrelatedFilesInNonEmptyOutputRoots() throws Exception {
        Path canonical = temporaryDirectory.resolve("canonical");
        createCanonicalTree(canonical, false);
        Path outputs = temporaryDirectory.resolve("outputs");
        write(outputs.resolve("sources/user-owned.txt"), "source sentinel\n");
        write(outputs.resolve("resources/user-owned.txt"), "resource sentinel\n");

        GenerationResult result = generate(canonical, outputs);

        assertEquals(5, result.files().size());
        assertEquals("source sentinel\n", Files.readString(outputs.resolve("sources/user-owned.txt")));
        assertEquals("resource sentinel\n", Files.readString(outputs.resolve("resources/user-owned.txt")));
    }

    @Test
    void rejectsSymlinkInCanonicalInputWithoutFollowingIt() throws Exception {
        Path canonical = temporaryDirectory.resolve("canonical");
        createCanonicalTree(canonical, false);
        Path externalFile = temporaryDirectory.resolve("external-secret.txt");
        write(externalFile, "must not be copied\n");
        Path link = canonical.resolve("src/canonical/resources/escaping-link.txt");
        try {
            Files.createSymbolicLink(link, externalFile);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.assumeTrue(false, "Symbolic links unavailable for this test: " + exception.getMessage());
        }

        BridgeGenerationException exception = assertThrows(BridgeGenerationException.class,
                () -> generate(canonical, temporaryDirectory.resolve("symlink-output")));

        assertTrue(exception.getMessage().contains("Symbolic links are forbidden"));
        assertFalse(Files.exists(temporaryDirectory.resolve("symlink-output")));
    }

    @Test
    void rejectsMissingManifest() throws Exception {
        Path canonical = temporaryDirectory.resolve("canonical");
        createCanonicalTree(canonical, false);
        Files.delete(canonical.resolve("MANIFEST.sha256"));

        BridgeGenerationException exception = assertThrows(BridgeGenerationException.class,
                () -> generate(canonical, temporaryDirectory.resolve("missing-manifest-output")));

        assertTrue(exception.getMessage().contains("Canonical manifest does not exist"));
    }

    @Test
    void rejectsManifestHashMismatchBeforeWriting() throws Exception {
        Path canonical = temporaryDirectory.resolve("canonical");
        createCanonicalTree(canonical, false);
        write(canonical.resolve("src/canonical/resources/nested/data.txt"), "tampered\n");
        Path output = temporaryDirectory.resolve("mismatch-output");

        BridgeGenerationException exception = assertThrows(BridgeGenerationException.class,
                () -> generate(canonical, output));

        assertTrue(exception.getMessage().contains("SHA-256 mismatch"));
        assertFalse(Files.exists(output));
    }

    @Test
    void rejectsManifestEntryWhoseFileIsMissing() throws Exception {
        Path canonical = temporaryDirectory.resolve("canonical");
        createCanonicalTree(canonical, false);
        Files.delete(canonical.resolve("src/canonical/resources/nested/data.txt"));

        BridgeGenerationException exception = assertThrows(BridgeGenerationException.class,
                () -> generate(canonical, temporaryDirectory.resolve("missing-file-output")));

        assertTrue(exception.getMessage().contains("manifest lists missing canonical file"));
    }

    @Test
    void rejectsCanonicalFileMissingFromManifest() throws Exception {
        Path canonical = temporaryDirectory.resolve("canonical");
        createCanonicalTree(canonical, false);
        write(canonical.resolve("src/canonical/resources/extra.txt"), "unreviewed\n");

        BridgeGenerationException exception = assertThrows(BridgeGenerationException.class,
                () -> generate(canonical, temporaryDirectory.resolve("extra-file-output")));

        assertTrue(exception.getMessage().contains("canonical file is not listed in manifest"));
    }

    @Test
    void rejectsUnsafePathInsideManifest() throws Exception {
        Path canonical = temporaryDirectory.resolve("canonical");
        createCanonicalTree(canonical, false);
        Files.writeString(canonical.resolve("MANIFEST.sha256"),
                "0000000000000000000000000000000000000000000000000000000000000000  "
                        + "src/canonical/java/../../escaped.java\n",
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);

        BridgeGenerationException exception = assertThrows(BridgeGenerationException.class,
                () -> generate(canonical, temporaryDirectory.resolve("unsafe-manifest-output")));

        assertTrue(exception.getMessage().contains("unsafe or non-normalized path"));
    }

    @Test
    void rejectsCaseCollidingPathsInsideManifest() throws Exception {
        Path canonical = temporaryDirectory.resolve("canonical");
        createCanonicalTree(canonical, false);
        String original = Files.readAllLines(canonical.resolve("MANIFEST.sha256"), StandardCharsets.UTF_8).get(0);
        int pathOffset = original.indexOf("  ") + 2;
        String originalPath = original.substring(pathOffset);
        int fileNameOffset = originalPath.lastIndexOf('/') + 1;
        String caseCollision = original.substring(0, pathOffset)
                + originalPath.substring(0, fileNameOffset)
                + originalPath.substring(fileNameOffset).toUpperCase(java.util.Locale.ROOT);
        Files.writeString(canonical.resolve("MANIFEST.sha256"), caseCollision + "\n",
                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);

        BridgeGenerationException exception = assertThrows(BridgeGenerationException.class,
                () -> generate(canonical, temporaryDirectory.resolve("case-collision-output")));

        assertTrue(exception.getMessage().contains("duplicate or case-colliding path"));
    }

    @Test
    void rejectsUnhandledDeclarationsEvenWithValidManifestBeforeWriting() throws Exception {
        Path canonical = temporaryDirectory.resolve("canonical");
        createCanonicalTree(canonical, false);
        write(canonical.resolve("src/canonical/java/example/Unreviewed.java"),
                "package example; public class Unreviewed {}\n");
        writeManifest(canonical);
        Path output = temporaryDirectory.resolve("unhandled-output");

        BridgeGenerationException exception = assertThrows(BridgeGenerationException.class,
                () -> generate(canonical, output));

        assertTrue(exception.getMessage().contains("1.21.4-fabric"));
        assertTrue(exception.getMessage().contains("example/Unreviewed.java"));
        assertTrue(exception.getMessage().contains("Canonical source copying is forbidden"));
        assertFalse(Files.exists(output));
    }

    private GenerationResult generate(Path canonical, Path output) throws BridgeGenerationException {
        return new BridgeCompiler().generate(new GenerationRequest(
                "1.21.4-fabric",
                canonical,
                output.resolve("sources"),
                output.resolve("resources")));
    }

    private static void createCanonicalTree(Path root, boolean reverseOrder) throws IOException {
        Path javaFile = root.resolve(
                "src/canonical/java/uk/co/enderfall/sdk/runtime/fabric/v1_21_4/EnderfallFabricRuntime.java");
        Path metadata = root.resolve("src/canonical/resources/fabric.mod.json");
        Path packMetadata = root.resolve("src/canonical/resources/pack.mcmeta");
        Path nestedResource = root.resolve("src/canonical/resources/nested/data.txt");
        String javaSource = "// Runtime entrypoint declaration; body is not an emission template.\n";
        if (reverseOrder) {
            write(nestedResource, "data\n");
            write(packMetadata, "{\"canonicalPack\":\"fixture-only\"}\n");
            write(metadata, "{\"canonical\":\"fixture-only\"}\n");
            write(javaFile, javaSource);
        } else {
            write(javaFile, javaSource);
            write(metadata, "{\"canonical\":\"fixture-only\"}\n");
            write(packMetadata, "{\"canonicalPack\":\"fixture-only\"}\n");
            write(nestedResource, "data\n");
        }
        writeManifest(root);
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static void writeManifest(Path root) throws IOException {
        Path canonical = root.resolve("src");
        List<Path> files;
        try (var stream = Files.walk(canonical)) {
            files = stream.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .sorted()
                    .toList();
        }
        List<String> lines = new ArrayList<>();
        for (Path relative : files) {
            byte[] content = Files.readAllBytes(root.resolve(relative));
            lines.add(sha256(content) + "  " + relative.toString().replace('\\', '/'));
        }
        write(root.resolve("MANIFEST.sha256"), String.join("\n", lines) + "\n");
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void assertTreesEqual(Path firstRoot, Path secondRoot) throws IOException {
        List<Path> firstFiles;
        try (var stream = Files.walk(firstRoot)) {
            firstFiles = stream.filter(Files::isRegularFile)
                    .map(firstRoot::relativize)
                    .sorted()
                    .toList();
        }
        List<Path> secondFiles;
        try (var stream = Files.walk(secondRoot)) {
            secondFiles = stream.filter(Files::isRegularFile)
                    .map(secondRoot::relativize)
                    .sorted()
                    .toList();
        }
        assertEquals(firstFiles, secondFiles);
        for (Path relative : firstFiles) {
            assertArrayEquals(Files.readAllBytes(firstRoot.resolve(relative)),
                    Files.readAllBytes(secondRoot.resolve(relative)));
        }
    }

    private static void assertNoTemporaryFiles(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            assertFalse(stream.anyMatch(path -> path.getFileName().toString().startsWith(".enderfall-bridge-")));
        }
    }
}
