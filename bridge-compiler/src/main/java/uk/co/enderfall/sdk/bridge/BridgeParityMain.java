package uk.co.enderfall.sdk.bridge;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Configuration-cache-friendly command line for generated-runtime parity gates. */
public final class BridgeParityMain {
    private static final String USAGE = "Usage: BridgeParityMain "
            + "[--forbidden-root <path>]... "
            + "[--expected <directory> --actual <directory>]... "
            + "[--expected-file <file> --actual-file <file>]...";

    private BridgeParityMain() {
    }

    /** Runs parity verification and exits non-zero for mismatches or invalid arguments. */
    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream output, PrintStream error) {
        if (args.length == 1 && args[0].equals("--help")) {
            output.println(USAGE);
            return 0;
        }
        final ParsedArguments parsed;
        try {
            parsed = parse(args);
        } catch (IllegalArgumentException exception) {
            error.println("Invalid bridge parity arguments: " + exception.getMessage());
            error.println(USAGE);
            return 2;
        }

        BridgeParity.Result result = new BridgeParity().verify(
                parsed.forbiddenRoots(), parsed.directoryPairs(), parsed.filePairs());
        if (!result.matches()) {
            error.println("EnderFall bridge parity failed with " + result.violations().size() + " problem(s):");
            for (String violation : result.violations()) {
                error.println("  - " + violation);
            }
            return 1;
        }
        output.println("Bridge parity verified: " + parsed.directoryPairs().size() + " directory pair(s), "
                + parsed.filePairs().size() + " file pair(s), " + result.comparedFiles()
                + " expected file(s), " + parsed.forbiddenRoots().size() + " forbidden root(s) absent");
        return 0;
    }

    private static ParsedArguments parse(String[] args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("Every option must have a value");
        }
        List<Path> forbiddenRoots = new ArrayList<>();
        List<Path> expected = new ArrayList<>();
        List<Path> actual = new ArrayList<>();
        List<Path> expectedFiles = new ArrayList<>();
        List<Path> actualFiles = new ArrayList<>();
        for (int index = 0; index < args.length; index += 2) {
            String option = args[index];
            String value = args[index + 1];
            if (value.isBlank()) {
                throw new IllegalArgumentException(option + " cannot have a blank value");
            }
            switch (option) {
                case "--forbidden-root" -> forbiddenRoots.add(Path.of(value));
                case "--expected" -> expected.add(Path.of(value));
                case "--actual" -> actual.add(Path.of(value));
                case "--expected-file" -> expectedFiles.add(Path.of(value));
                case "--actual-file" -> actualFiles.add(Path.of(value));
                default -> throw new IllegalArgumentException("Unknown option: " + option);
            }
        }
        if (expected.isEmpty() && expectedFiles.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one directory pair or --expected-file/--actual-file pair is required");
        }
        if (expected.size() != actual.size()) {
            throw new IllegalArgumentException("Expected " + expected.size() + " --actual value(s), found "
                    + actual.size());
        }
        if (expectedFiles.size() != actualFiles.size()) {
            throw new IllegalArgumentException("Expected " + expectedFiles.size()
                    + " --actual-file value(s), found " + actualFiles.size());
        }
        List<BridgeParity.DirectoryPair> directoryPairs = new ArrayList<>(expected.size());
        for (int index = 0; index < expected.size(); index++) {
            directoryPairs.add(new BridgeParity.DirectoryPair(expected.get(index), actual.get(index)));
        }
        List<BridgeParity.FilePair> filePairs = new ArrayList<>(expectedFiles.size());
        for (int index = 0; index < expectedFiles.size(); index++) {
            filePairs.add(new BridgeParity.FilePair(expectedFiles.get(index), actualFiles.get(index)));
        }
        return new ParsedArguments(
                List.copyOf(forbiddenRoots), List.copyOf(directoryPairs), List.copyOf(filePairs));
    }

    private record ParsedArguments(
            List<Path> forbiddenRoots,
            List<BridgeParity.DirectoryPair> directoryPairs,
            List<BridgeParity.FilePair> filePairs) {
    }
}
