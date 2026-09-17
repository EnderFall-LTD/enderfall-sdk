package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class TargetCoordinateMainTest {
    private static final String EXPECTED_1_21_1_FABRIC = lines(
            "targetId=1.21.1-fabric",
            "minecraftVersion=1.21.1",
            "javaVersion=21",
            "loader=fabric",
            "loaderVersion=0.19.5",
            "platformApiVersion=0.116.17+1.21.1",
            "loaderAbi=FABRIC",
            "minecraftAbi=V1_21_1",
            "mappingAbi=MOJANG_REMAPPED",
            "recipeAbi=CODEC_1_21_1",
            "networkAbi=FABRIC_TYPED_PAYLOAD",
            "menuAbi=V1_21_1");

    @Test
    void printsAllReviewedFieldsInDeterministicOrder() {
        CapturedOutput captured = new CapturedOutput();

        int exitCode = TargetCoordinateMain.run(
                new String[] {"--target", "1.21.1-fabric"}, captured.output(), captured.error());

        assertEquals(0, exitCode);
        assertEquals(EXPECTED_1_21_1_FABRIC, captured.standardOutput());
        assertEquals("", captured.standardError());
    }

    @Test
    void acceptsRepeatableMatchingExpectationsAndStillPrintsCanonicalOrder() {
        CapturedOutput captured = new CapturedOutput();

        int exitCode = TargetCoordinateMain.run(new String[] {
                "--target", "1.21.1-fabric",
                "--expect", "recipeAbi=CODEC_1_21_1",
                "--expect", "minecraftVersion=1.21.1",
                "--expect", "platformApiVersion=0.116.17+1.21.1",
                "--expect", "loader=fabric"
        }, captured.output(), captured.error());

        assertEquals(0, exitCode);
        assertEquals(EXPECTED_1_21_1_FABRIC, captured.standardOutput());
        assertEquals("", captured.standardError());
    }

    @Test
    void acceptsAnExpectedEmptyPlatformApiVersion() {
        CapturedOutput captured = new CapturedOutput();

        int exitCode = TargetCoordinateMain.run(new String[] {
                "--target", "1.21.1-neoforge",
                "--expect", "platformApiVersion="
        }, captured.output(), captured.error());

        assertEquals(0, exitCode);
        assertTrue(captured.standardOutput().contains(lines("platformApiVersion=")));
        assertEquals("", captured.standardError());
    }

    @Test
    void mismatchFailsWithoutMachineReadableOutput() {
        CapturedOutput captured = new CapturedOutput();

        int exitCode = TargetCoordinateMain.run(new String[] {
                "--target", "1.21.1-fabric",
                "--expect", "loaderVersion=0.0.0"
        }, captured.output(), captured.error());

        assertEquals(2, exitCode);
        assertEquals("", captured.standardOutput());
        assertTrue(captured.standardError().contains(
                "Target coordinate mismatch for 'loaderVersion': expected '0.0.0', actual '0.19.5'"));
    }

    @Test
    void unknownExpectationKeyFailsAndListsKnownKeys() {
        CapturedOutput captured = new CapturedOutput();

        int exitCode = TargetCoordinateMain.run(new String[] {
                "--target", "1.21.1-fabric",
                "--expect", "fabricVersion=0.116.17+1.21.1"
        }, captured.output(), captured.error());

        assertEquals(2, exitCode);
        assertEquals("", captured.standardOutput());
        assertTrue(captured.standardError().contains("Unknown target coordinate key 'fabricVersion'"));
        assertTrue(captured.standardError().contains("targetId, minecraftVersion, javaVersion"));
    }

    @Test
    void duplicateExpectationKeyFailsEvenWhenValuesMatch() {
        CapturedOutput captured = new CapturedOutput();

        int exitCode = TargetCoordinateMain.run(new String[] {
                "--target", "1.21.1-fabric",
                "--expect", "loader=fabric",
                "--expect", "loader=fabric"
        }, captured.output(), captured.error());

        assertEquals(2, exitCode);
        assertEquals("", captured.standardOutput());
        assertTrue(captured.standardError().contains("Duplicate target coordinate expectation: loader"));
    }

    @Test
    void unsafeExpectationValueCannotInjectAnotherOutputLine() {
        CapturedOutput captured = new CapturedOutput();

        int exitCode = TargetCoordinateMain.run(new String[] {
                "--target", "1.21.1-fabric",
                "--expect", "loader=fabric\nforgedOutputLine"
        }, captured.output(), captured.error());

        assertEquals(2, exitCode);
        assertEquals("", captured.standardOutput());
        assertTrue(captured.standardError().contains("outside the safe key=value format"));
        assertTrue(captured.standardError().contains("loader=fabric\\nforgedOutputLine"));
        assertFalse(captured.standardError().contains("loader=fabric\nforgedOutputLine"));
    }

    @Test
    void unknownTargetFailsWithoutOutputAndListsReviewedTargets() {
        CapturedOutput captured = new CapturedOutput();

        int exitCode = TargetCoordinateMain.run(
                new String[] {"--target", "1.21.2-fabric"}, captured.output(), captured.error());

        assertEquals(2, exitCode);
        assertEquals("", captured.standardOutput());
        assertTrue(captured.standardError().contains("Unknown target '1.21.2-fabric'"));
        assertTrue(captured.standardError().contains("1.21.1-fabric"));
    }

    private static String lines(String... values) {
        return String.join(System.lineSeparator(), List.of(values)) + System.lineSeparator();
    }

    private static final class CapturedOutput {
        private final ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        private final ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
        private final PrintStream output = new PrintStream(outputBytes, true, StandardCharsets.UTF_8);
        private final PrintStream error = new PrintStream(errorBytes, true, StandardCharsets.UTF_8);

        PrintStream output() {
            return output;
        }

        PrintStream error() {
            return error;
        }

        String standardOutput() {
            return outputBytes.toString(StandardCharsets.UTF_8);
        }

        String standardError() {
            return errorBytes.toString(StandardCharsets.UTF_8);
        }
    }
}
