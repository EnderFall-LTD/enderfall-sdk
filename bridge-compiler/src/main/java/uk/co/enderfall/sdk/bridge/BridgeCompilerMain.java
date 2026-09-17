package uk.co.enderfall.sdk.bridge;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Command-line entrypoint used by generated target Gradle projects. */
public final class BridgeCompilerMain {
    private static final String USAGE = "Usage: BridgeCompilerMain --target <id> --canonical-root <path> "
            + "--output-sources <path> --output-resources <path> [--persistence true|false]";

    private BridgeCompilerMain() {
    }

    /** Runs the compiler and exits non-zero for invalid arguments or generation failures. */
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
            Map<String, String> options = parse(args);
            GenerationRequest request = new GenerationRequest(
                    required(options, "--target"),
                    Path.of(required(options, "--canonical-root")),
                    Path.of(required(options, "--output-sources")),
                    Path.of(required(options, "--output-resources")),
                    persistence(options));
            GenerationResult result = new BridgeCompiler().generate(request);
            output.println("Generated " + result.files().size() + " files for " + result.target().id()
                    + " (SHA-256 " + result.sha256() + ")");
            return 0;
        } catch (IllegalArgumentException | BridgeGenerationException exception) {
            error.println("EnderFall bridge generation failed: " + exception.getMessage());
            error.println(USAGE);
            return 2;
        }
    }

    private static Map<String, String> parse(String[] args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("Every option must have a value");
        }
        Map<String, String> options = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index += 2) {
            String option = args[index];
            if (!option.equals("--target") && !option.equals("--canonical-root")
                    && !option.equals("--output-sources") && !option.equals("--output-resources") && !option.equals("--persistence")) {
                throw new IllegalArgumentException("Unknown option: " + option);
            }
            if (options.putIfAbsent(option, args[index + 1]) != null) {
                throw new IllegalArgumentException("Duplicate option: " + option);
            }
        }
        return options;
    }

    private static String required(Map<String, String> options, String option) {
        String value = options.get(option);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required option " + option);
        }
        return value;
    }

    private static boolean persistence(Map<String, String> options) {
        String value = options.getOrDefault("--persistence", "false");
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("--persistence must be true or false");
        return value.equals("true");
    }
}
