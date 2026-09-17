package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BridgeParityTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void verifiesRepeatableIdenticalDirectoryPairsAndAbsentForbiddenRoots() throws Exception {
        Path expectedSources = temporaryDirectory.resolve("expected-sources");
        Path actualSources = temporaryDirectory.resolve("actual-sources");
        Path expectedResources = temporaryDirectory.resolve("expected-resources");
        Path actualResources = temporaryDirectory.resolve("actual-resources");
        write(expectedSources.resolve("nested/Source.java"), "class Source {}\n");
        write(actualSources.resolve("nested/Source.java"), "class Source {}\n");
        write(expectedResources.resolve("fabric.mod.json"), "{}\n");
        write(actualResources.resolve("fabric.mod.json"), "{}\n");

        BridgeParity.Result result = new BridgeParity().verify(
                List.of(temporaryDirectory.resolve("removed-runtime-one"),
                        temporaryDirectory.resolve("removed-runtime-two")),
                List.of(
                        new BridgeParity.DirectoryPair(expectedSources, actualSources),
                        new BridgeParity.DirectoryPair(expectedResources, actualResources)));

        assertTrue(result.matches());
        assertEquals(2, result.comparedFiles());
        assertEquals(List.of(), result.violations());
    }

    @Test
    void reportsMissingUnexpectedAndChangedFilesTogetherInStableOrder() throws Exception {
        Path expected = temporaryDirectory.resolve("expected");
        Path actual = temporaryDirectory.resolve("actual");
        write(expected.resolve("a.txt"), "missing\n");
        write(expected.resolve("b.txt"), "expected\n");
        write(actual.resolve("b.txt"), "changed\n");
        write(actual.resolve("c.txt"), "unexpected\n");

        BridgeParity.Result result = new BridgeParity().verify(
                List.of(), List.of(new BridgeParity.DirectoryPair(expected, actual)));

        assertFalse(result.matches());
        assertEquals(3, result.violations().size());
        assertTrue(result.violations().get(0).contains("content mismatch for 'b.txt'"));
        assertTrue(result.violations().get(1).contains("unexpected generated file 'c.txt'"));
        assertTrue(result.violations().get(2).contains("missing generated file 'a.txt'"));
    }

    @Test
    void existingForbiddenRootFailsEvenWhenTreesMatch() throws Exception {
        Path expected = temporaryDirectory.resolve("expected");
        Path actual = temporaryDirectory.resolve("actual");
        Path forbidden = temporaryDirectory.resolve("runtime-fabric-1.21.4");
        write(expected.resolve("same.txt"), "same\n");
        write(actual.resolve("same.txt"), "same\n");
        Files.createDirectories(forbidden);

        BridgeParity.Result result = new BridgeParity().verify(
                List.of(forbidden), List.of(new BridgeParity.DirectoryPair(expected, actual)));

        assertFalse(result.matches());
        assertEquals(1, result.violations().size());
        assertTrue(result.violations().get(0).contains("forbidden handwritten root still exists"));
    }

    @Test
    void commandLineAcceptsRepeatablePairs() throws Exception {
        Path expectedOne = temporaryDirectory.resolve("expected-one");
        Path actualOne = temporaryDirectory.resolve("actual-one");
        Path expectedTwo = temporaryDirectory.resolve("expected-two");
        Path actualTwo = temporaryDirectory.resolve("actual-two");
        write(expectedOne.resolve("one.txt"), "one\n");
        write(actualOne.resolve("one.txt"), "one\n");
        write(expectedTwo.resolve("two.txt"), "two\n");
        write(actualTwo.resolve("two.txt"), "two\n");
        CapturedOutput captured = new CapturedOutput();

        int exitCode = BridgeParityMain.run(new String[] {
                "--forbidden-root", temporaryDirectory.resolve("absent").toString(),
                "--expected", expectedOne.toString(),
                "--actual", actualOne.toString(),
                "--expected", expectedTwo.toString(),
                "--actual", actualTwo.toString()
        }, captured.output(), captured.error());

        assertEquals(0, exitCode);
        assertTrue(captured.outputText().contains(
                "2 directory pair(s), 0 file pair(s), 2 expected file(s)"));
        assertEquals("", captured.errorText());
    }

    @Test
    void commandLineReturnsOneForParityFailure() throws Exception {
        Path expected = temporaryDirectory.resolve("expected");
        Path actual = temporaryDirectory.resolve("actual");
        write(expected.resolve("same.txt"), "expected\n");
        write(actual.resolve("same.txt"), "different\n");
        CapturedOutput captured = new CapturedOutput();

        int exitCode = BridgeParityMain.run(new String[] {
                "--expected", expected.toString(), "--actual", actual.toString()
        }, captured.output(), captured.error());

        assertEquals(1, exitCode);
        assertTrue(captured.errorText().contains("bridge parity failed"));
        assertTrue(captured.errorText().contains("content mismatch"));
    }

    @Test
    void commandLineReturnsTwoForUnpairedDirectories() {
        CapturedOutput captured = new CapturedOutput();

        int exitCode = BridgeParityMain.run(new String[] {
                "--expected", temporaryDirectory.resolve("expected").toString()
        }, captured.output(), captured.error());

        assertEquals(2, exitCode);
        assertTrue(captured.errorText().contains("Expected 1 --actual value(s), found 0"));
    }

    @Test
    void verifiesRepeatableRegularFilePairsWithoutDirectoryPairs() throws Exception {
        Path expectedJar = temporaryDirectory.resolve("reference-runtime.jar");
        Path actualJar = temporaryDirectory.resolve("generated-runtime.jar");
        Path expectedChecksum = temporaryDirectory.resolve("reference.sha256");
        Path actualChecksum = temporaryDirectory.resolve("generated.sha256");
        write(expectedJar, "identical jar bytes\n");
        write(actualJar, "identical jar bytes\n");
        write(expectedChecksum, "checksum\n");
        write(actualChecksum, "checksum\n");
        CapturedOutput captured = new CapturedOutput();

        int exitCode = BridgeParityMain.run(new String[] {
                "--expected-file", expectedJar.toString(),
                "--actual-file", actualJar.toString(),
                "--expected-file", expectedChecksum.toString(),
                "--actual-file", actualChecksum.toString()
        }, captured.output(), captured.error());

        assertEquals(0, exitCode);
        assertTrue(captured.outputText().contains(
                "0 directory pair(s), 2 file pair(s), 2 expected file(s)"));
        assertEquals("", captured.errorText());
    }

    @Test
    void reportsDirectFileSizeAndHashMismatch() throws Exception {
        Path expected = temporaryDirectory.resolve("reference.jar");
        Path actual = temporaryDirectory.resolve("generated.jar");
        write(expected, "reference bytes\n");
        write(actual, "different generated bytes\n");

        BridgeParity.Result result = new BridgeParity().verify(
                List.of(), List.of(), List.of(new BridgeParity.FilePair(expected, actual)));

        assertFalse(result.matches());
        assertEquals(1, result.comparedFiles());
        assertEquals(1, result.violations().size());
        assertTrue(result.violations().get(0).contains("file comparison 1"));
        assertTrue(result.violations().get(0).contains("content mismatch: expected SHA-256"));
        assertTrue(result.violations().get(0).contains("bytes), found"));
    }

    @Test
    void rejectsMissingOrNonRegularDirectFiles() throws Exception {
        Path expectedDirectory = temporaryDirectory.resolve("not-a-file");
        Path missingActual = temporaryDirectory.resolve("missing.jar");
        Files.createDirectories(expectedDirectory);

        BridgeParity.Result result = new BridgeParity().verify(
                List.of(), List.of(), List.of(new BridgeParity.FilePair(expectedDirectory, missingActual)));

        assertFalse(result.matches());
        assertEquals(0, result.comparedFiles());
        assertEquals(2, result.violations().size());
        assertTrue(result.violations().stream().allMatch(
                violation -> violation.contains("does not exist or is not a regular file")));
    }

    @Test
    void commandLineReturnsTwoForUnpairedFiles() {
        CapturedOutput captured = new CapturedOutput();

        int exitCode = BridgeParityMain.run(new String[] {
                "--expected-file", temporaryDirectory.resolve("expected.jar").toString()
        }, captured.output(), captured.error());

        assertEquals(2, exitCode);
        assertTrue(captured.errorText().contains("Expected 1 --actual-file value(s), found 0"));
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static final class CapturedOutput {
        private final ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        private final ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
        private final PrintStream output = new PrintStream(outputBytes, true, StandardCharsets.UTF_8);
        private final PrintStream error = new PrintStream(errorBytes, true, StandardCharsets.UTF_8);

        private PrintStream output() {
            return output;
        }

        private PrintStream error() {
            return error;
        }

        private String outputText() {
            return outputBytes.toString(StandardCharsets.UTF_8);
        }

        private String errorText() {
            return errorBytes.toString(StandardCharsets.UTF_8);
        }
    }
}
