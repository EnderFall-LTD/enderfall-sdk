package uk.co.enderfall.sdk.harness;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;

/** Audits historical focused-gameplay evidence, not current runtime binaries or release readiness. */
public final class GameplayMatrixMain {
    private static final int MAX_REPORT_BYTES = 1_048_576;
    private static final int MAX_LOG_BYTES = 16 * MAX_REPORT_BYTES;
    private static final List<String> TARGETS = TargetCatalog.standard().targetIds().stream().sorted().toList();

    private GameplayMatrixMain() { }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length < 3) {
            throw new IllegalArgumentException("Supply -Penderfall.gameplayReports=<oldest.json>,<newer.json>,...; "
                    + "explicit order is required and the latest attempt for each target wins, even if it failed");
        }
        Path sdkRoot = Path.of(arguments[0]).toAbsolutePath().normalize();
        Path output = Path.of(arguments[1]).toAbsolutePath().normalize();
        Path build = sdkRoot.resolve("build").toRealPath();
        if (!output.startsWith(build) || output.equals(build)) {
            throw new IllegalArgumentException("Matrix output must be a file inside " + build);
        }
        List<Path> inputs = new ArrayList<>();
        for (int index = 2; index < arguments.length; index++) {
            Path input = sdkRoot.resolve(arguments[index]).toAbsolutePath().normalize();
            if (input.equals(output) || (Files.exists(input) && Files.exists(output) && Files.isSameFile(input, output))) {
                throw new IllegalArgumentException("Matrix output cannot overwrite an input report");
            }
            inputs.add(input);
        }
        Map<String, Object> matrix = audit(sdkRoot, inputs);
        Path ancestor = output.getParent();
        while (!Files.exists(ancestor)) ancestor = ancestor.getParent();
        require(ancestor.toRealPath().startsWith(build), "Matrix output parent escapes the build directory");
        Files.createDirectories(output.getParent());
        if (!output.getParent().toRealPath().startsWith(build) || Files.isSymbolicLink(output)) {
            throw new IllegalArgumentException("Matrix output escapes the build directory through a link");
        }
        Files.writeString(output, JsonOutput.prettyPrint(JsonOutput.toJson(matrix)) + "\n", StandardCharsets.UTF_8);
        if (!Boolean.TRUE.equals(matrix.get("passed"))) {
            throw new IllegalStateException("Gameplay matrix failed: " + matrix.get("failures") + "; see " + output);
        }
        System.out.println("PASS focused gameplay matrix: " + TARGETS.size() + " targets, portable source "
                + matrix.get("portableSourceHash") + "; see " + output);
    }

    static Map<String, Object> audit(Path sdkRoot, List<Path> reports) {
        List<String> failures = new ArrayList<>();
        List<Map<String, Object>> reportEvidence = new ArrayList<>();
        Map<String, Attempt> latest = new LinkedHashMap<>();
        Set<Path> seenFiles = new LinkedHashSet<>();
        if (reports.isEmpty()) failures.add("No input reports supplied");
        for (Path report : reports) {
            try {
                Path resolved = report.toRealPath();
                require(seenFiles.add(resolved), "Repeated report file " + report);
                String text = readBounded(resolved, MAX_REPORT_BYTES);
                Map<?, ?> document = object(new JsonSlurper().parseText(text), "report");
                require(integer(document.get("schemaVersion"), 1), "Unsupported report schema");
                Object values = document.get("results");
                require(values instanceof List<?> && !((List<?>) values).isEmpty(), "Missing or empty results");
                String reportHash = sha256(text);
                Set<String> seenTargets = new LinkedHashSet<>();
                for (Object value : (List<?>) values) {
                    Map<?, ?> row = object(value, "result");
                    String target = string(row.get("target"), "target");
                    require(TARGETS.contains(target), "Unknown target " + target);
                    require(seenTargets.add(target), "Duplicate target inside one report: " + target);
                    latest.put(target, new Attempt(row, report.toString(), reportHash));
                }
                reportEvidence.add(Map.of("path", report.toString(), "sha256", reportHash));
            } catch (IOException | RuntimeException failure) {
                failures.add("Report " + report + ": " + failure.getMessage());
            }
        }
        String sourceHash = "";
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String target : TARGETS) {
            Attempt attempt = latest.get(target);
            if (attempt == null) {
                failures.add("Missing target " + target);
                continue;
            }
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("target", target);
            evidence.put("sourceReport", attempt.path());
            evidence.put("sourceReportSha256", attempt.hash());
            try {
                Map<?, ?> row = attempt.row();
                for (String field : List.of("passed", "serverReady", "serverRoundTripComplete",
                        "clientRoundTripComplete", "menuOpened")) {
                    require(Boolean.TRUE.equals(row.get(field)), field + " is not true");
                }
                require(integer(row.get("serverExitCode"), 0) && integer(row.get("clientExitCode"), 0),
                        "Client/server did not both exit with code 0");
                require("".equals(row.get("failure")), "Run includes a failure or lacks its failure field");
                String hash = string(row.get("portableSourceHash"), "portableSourceHash");
                require(hash.matches("[0-9a-f]{64}"), "Invalid portable source hash");
                if (sourceHash.isEmpty()) sourceHash = hash;
                require(hash.equals(sourceHash), "Portable sources differ between targets");
                GameplayEvidence expected = new GameplayEvidence(target);
                Map<?, ?> checks = object(row.get("gameplayChecks"), "gameplayChecks");
                require(checks.keySet().equals(expected.snapshot().keySet()), "Missing or unrecognized gameplay checks");
                require(checks.values().stream().allMatch(Boolean.TRUE::equals), "A gameplay check did not pass");
                List<String> serverMarkers = new ArrayList<>(expected.serverMarkers().keySet());
                serverMarkers.add("Lifecycle SERVER_STARTED on " + target);
                serverMarkers.add("ENDERFALL_CONNECTION_SERVER_COMPLETE " + target);
                List<String> clientMarkers = new ArrayList<>(expected.clientMarkers().keySet());
                clientMarkers.add("ENDERFALL_CONNECTION_CLIENT_COMPLETE " + target);
                clientMarkers.add("ENDERFALL_MENU_CLIENT_OPEN " + target);
                evidence.put("serverLogSha256", verifyLog(sdkRoot, row.get("serverLog"), target, serverMarkers));
                evidence.put("clientLogSha256", verifyLog(sdkRoot, row.get("clientLog"), target, clientMarkers));
                evidence.put("portableSourceHash", hash);
                evidence.put("gameplayChecks", checks);
                evidence.put("serverLog", row.get("serverLog"));
                evidence.put("clientLog", row.get("clientLog"));
                evidence.put("passed", true);
            } catch (IOException | RuntimeException failure) {
                evidence.put("passed", false);
                evidence.put("failure", failure.getMessage());
                failures.add(target + ": " + failure.getMessage());
            }
            rows.add(evidence);
        }
        Map<String, Object> matrix = new LinkedHashMap<>();
        matrix.put("schemaVersion", 1);
        matrix.put("scope", "historical-focused-generated-runtime-gameplay");
        matrix.put("passed", failures.isEmpty() && rows.size() == TARGETS.size());
        matrix.put("expectedTargets", TARGETS);
        matrix.put("portableSourceHash", sourceHash);
        matrix.put("reportsInSuppliedOrder", reportEvidence);
        matrix.put("results", rows);
        matrix.put("failures", failures);
        return matrix;
    }

    private static String verifyLog(Path sdkRoot, Object value, String target, List<String> markers) throws IOException {
        Path relative = Path.of(string(value, "log path"));
        require(!relative.isAbsolute(), "Log path must be relative to SDK root");
        Path build = sdkRoot.resolve("build").toRealPath();
        Path log = sdkRoot.resolve(relative).normalize();
        require(log.startsWith(build) && log.toRealPath().startsWith(build), "Log path escapes SDK build directory");
        String text = readBounded(log, MAX_LOG_BYTES);
        require(!containsMarker(text, "ENDERFALL_GAMEPLAY_FAILED " + target), "Log contains a gameplay failure");
        for (String marker : markers) require(containsMarker(text, marker), "Log lacks checkpoint " + marker);
        return sha256(text);
    }

    private static boolean containsMarker(String text, String marker) {
        return Pattern.compile(Pattern.quote(marker) + "(?=\\s|$)").matcher(text).find();
    }

    private static String readBounded(Path path, int limit) throws IOException {
        require(Files.isRegularFile(path) && Files.size(path) <= limit, "Missing or oversized evidence file " + path);
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(limit + 1);
            require(bytes.length <= limit, "Evidence file grew beyond its size limit: " + path);
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static boolean integer(Object value, int expected) {
        return value instanceof Number && value.toString().equals(Integer.toString(expected));
    }

    private static String string(Object value, String description) {
        require(value instanceof String, "Expected string " + description);
        return (String) value;
    }

    private static Map<?, ?> object(Object value, String description) {
        require(value instanceof Map<?, ?>, "Expected object " + description);
        return (Map<?, ?>) value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record Attempt(Map<?, ?> row, String path, String hash) { }
}
