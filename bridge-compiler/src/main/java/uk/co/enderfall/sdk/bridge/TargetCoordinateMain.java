package uk.co.enderfall.sdk.bridge;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Command-line verifier for Gradle target coordinates and reviewed Java catalog records. */
public final class TargetCoordinateMain {
    private static final String USAGE = "Usage: TargetCoordinateMain --target <id> "
            + "[--expect <key=value>]...";

    private TargetCoordinateMain() {
    }

    /** Prints reviewed coordinates, exiting non-zero for invalid arguments or failed expectations. */
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
        try {
            ParsedArguments parsed = parse(args);
            Map<String, String> fields = TargetCoordinateVerifier.reviewedFields(parsed.targetId());
            TargetCoordinateVerifier.verifyExpectations(fields, parsed.expectations());
            for (Map.Entry<String, String> field : fields.entrySet()) {
                output.println(field.getKey() + '=' + field.getValue());
            }
            return 0;
        } catch (IllegalArgumentException exception) {
            error.println("EnderFall target coordinate verification failed: " + exception.getMessage());
            error.println(USAGE);
            return 2;
        }
    }

    private static ParsedArguments parse(String[] args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("Every option must have a value");
        }
        String targetId = null;
        List<String> expectations = new ArrayList<>();
        for (int index = 0; index < args.length; index += 2) {
            String option = args[index];
            String value = args[index + 1];
            switch (option) {
                case "--target" -> {
                    if (targetId != null) {
                        throw new IllegalArgumentException("Duplicate option: --target");
                    }
                    if (value.isBlank()) {
                        throw new IllegalArgumentException("Missing required option --target");
                    }
                    targetId = value;
                }
                case "--expect" -> expectations.add(value);
                default -> throw new IllegalArgumentException("Unknown option: " + option);
            }
        }
        if (targetId == null) {
            throw new IllegalArgumentException("Missing required option --target");
        }
        return new ParsedArguments(targetId, List.copyOf(expectations));
    }

    private record ParsedArguments(String targetId, List<String> expectations) {
    }
}
