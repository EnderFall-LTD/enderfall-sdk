package uk.co.enderfall.sdk.harness;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import java.io.IOException;
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
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;

/** Fail-closed audit of release evidence produced by the current GitHub workflow. */
public final class ReleaseMatrixMain {
    private static final int MAX_REPORT_BYTES = 1_048_576;
    private static final int MAX_LOG_BYTES = 16 * MAX_REPORT_BYTES;
    private static final List<String> TARGETS = TargetCatalog.standard().targetIds().stream().sorted().toList();

    private ReleaseMatrixMain() { }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 4) {
            throw new IllegalArgumentException(
                    "Usage: ReleaseMatrixMain <sdk-root> <evidence-root> <expected-revision> <output>");
        }
        Path sdkRoot = Path.of(arguments[0]).toAbsolutePath().normalize();
        Path evidenceRoot = Path.of(arguments[1]).toAbsolutePath().normalize();
        String revision = arguments[2];
        Path output = Path.of(arguments[3]).toAbsolutePath().normalize();
        require(revision.matches("[0-9a-f]{40}"), "Expected revision must be a full lowercase Git SHA");
        require(output.startsWith(sdkRoot.resolve("build").toAbsolutePath().normalize()),
                "Release matrix output must stay under the SDK build directory");
        Map<String, Object> matrix = audit(evidenceRoot, revision);
        Files.createDirectories(output.getParent());
        Files.writeString(output, JsonOutput.prettyPrint(JsonOutput.toJson(matrix)) + "\n", StandardCharsets.UTF_8);
        if (!Boolean.TRUE.equals(matrix.get("passed"))) {
            throw new IllegalStateException("Release runtime matrix failed: " + matrix.get("failures")
                    + "; see " + output);
        }
        System.out.println("PASS release runtime matrix for " + revision + ": " + TARGETS.size()
                + " targets, portable source " + matrix.get("portableSourceHash"));
    }

    static Map<String, Object> audit(Path evidenceRoot, String expectedRevision) {
        List<String> failures = new ArrayList<>();
        List<Map<String, Object>> results = new ArrayList<>();
        Set<String> seenTargets = new LinkedHashSet<>();
        String portableSourceHash = "";
        try {
            require(Files.isDirectory(evidenceRoot), "Missing evidence root " + evidenceRoot);
            List<Path> artifacts;
            try (var stream = Files.list(evidenceRoot)) {
                artifacts = stream.filter(Files::isDirectory).sorted().toList();
            }
            if (artifacts.size() != TARGETS.size()) {
                failures.add("Expected exactly " + TARGETS.size() + " target artifacts but found "
                        + artifacts.size());
            }
            for (Path artifact : artifacts) {
                Map<String, Object> result = new LinkedHashMap<>();
                try {
                    String revision = readBounded(artifact.resolve("revision.txt"), 128).trim();
                    require(revision.equals(expectedRevision), "Evidence revision " + revision
                            + " does not match " + expectedRevision);
                    Map<?, ?> server = singleResult(artifact.resolve("server/server.json"));
                    Map<?, ?> client = singleResult(artifact.resolve("client/client.json"));
                    Map<?, ?> gameplay = singleResult(artifact.resolve("gameplay/connection.json"));
                    String target = string(gameplay.get("target"), "gameplay target");
                    require(TARGETS.contains(target), "Unknown target " + target);
                    require(seenTargets.add(target), "Duplicate target evidence " + target);
                    require(target.equals(server.get("target")) && target.equals(client.get("target")),
                            "Target differs between evidence lanes");

                    requirePass(server, List.of("serverStarted", "stoppedCleanly"), "server");
                    require(number(server.get("exitCode")) == 0, "server exitCode is not zero");
                    Path serverLog = artifact.resolve("server/server").resolve(target + ".log");
                    String serverText = readBounded(serverLog, MAX_LOG_BYTES);
                    requireContains(serverText, "Lifecycle SERVER_STARTED on " + target, "server log");
                    requireContains(serverText, "ENDERFALL_GAMEPLAY_FOUNDATION_REGISTRATION_READY " + target,
                            "server log");
                    requireContains(serverText, "ENDERFALL_GAMEPLAY_FOUNDATION_CONFIG_READY " + target,
                            "server log");
                    requireContains(serverText, "ENDERFALL_GAMEPLAY_FOUNDATION_LISTENER_ISOLATION_READY " + target,
                            "server log");

                    requirePass(client, List.of("lifecycleAndTicksReady"), "client");
                    require(number(client.get("exitCode")) == 0, "client exitCode is not zero");
                    Path clientLog = artifact.resolve("client/client").resolve(target + ".log");
                    String clientText = readBounded(clientLog, MAX_LOG_BYTES);
                    requireContains(clientText, "ENDERFALL_CLIENT_SMOKE_READY " + target, "client log");
                    requireContains(clientText, "ENDERFALL_CLIENT_RESOURCES_RELOADED " + target + " 2", "client log");
                    requireContains(clientText, "ENDERFALL_CLIENT_SMOKE_COMPLETE " + target, "client log");

                    requirePass(gameplay, List.of("serverReady", "serverRoundTripComplete",
                            "clientRoundTripComplete", "menuOpened"), "gameplay");
                    require(number(gameplay.get("serverExitCode")) == 0
                                    && number(gameplay.get("clientExitCode")) == 0,
                            "gameplay client/server exit code is not zero");
                    String sourceHash = string(gameplay.get("portableSourceHash"), "portableSourceHash");
                    require(sourceHash.matches("[0-9a-f]{64}"), "Invalid portable source hash");
                    if (portableSourceHash.isEmpty()) portableSourceHash = sourceHash;
                    require(portableSourceHash.equals(sourceHash), "Portable source hashes differ between targets");
                    Map<?, ?> checks = object(gameplay.get("gameplayChecks"), "gameplayChecks");
                    Set<String> expectedChecks = new GameplayEvidence(target).snapshot().keySet();
                    require(checks.keySet().equals(expectedChecks), "Missing or unknown gameplay checks");
                    require(checks.values().stream().allMatch(Boolean.TRUE::equals), "A gameplay check is not true");
                    String gameplayServer = readBounded(
                            artifact.resolve("gameplay/connection").resolve(target + ".server.log"), MAX_LOG_BYTES);
                    String gameplayClient = readBounded(
                            artifact.resolve("gameplay/connection").resolve(target + ".client.log"), MAX_LOG_BYTES);
                    for (String marker : new GameplayEvidence(target).serverMarkers().keySet()) {
                        requireContains(gameplayServer, marker, "gameplay server log");
                    }
                    for (String marker : new GameplayEvidence(target).clientMarkers().keySet()) {
                        requireContains(gameplayClient, marker, "gameplay client log");
                    }
                    require(!gameplayServer.contains("ENDERFALL_GAMEPLAY_FAILED ")
                                    && !gameplayClient.contains("ENDERFALL_GAMEPLAY_FAILED "),
                            "Gameplay log contains a failure marker");

                    result.put("target", target);
                    result.put("revision", revision);
                    result.put("portableSourceHash", sourceHash);
                    result.put("serverReportSha256", sha256(artifact.resolve("server/server.json")));
                    result.put("clientReportSha256", sha256(artifact.resolve("client/client.json")));
                    result.put("gameplayReportSha256", sha256(artifact.resolve("gameplay/connection.json")));
                    result.put("passed", true);
                } catch (IOException | RuntimeException failure) {
                    result.put("artifact", artifact.getFileName().toString());
                    result.put("passed", false);
                    result.put("failure", failure.getMessage());
                    failures.add(artifact.getFileName() + ": " + failure.getMessage());
                }
                results.add(result);
            }
        } catch (IOException | RuntimeException failure) {
            failures.add(failure.getMessage());
        }
        for (String target : TARGETS) {
            if (!seenTargets.contains(target)) failures.add("Missing target " + target);
        }
        Map<String, Object> matrix = new LinkedHashMap<>();
        matrix.put("schemaVersion", 1);
        matrix.put("revision", expectedRevision);
        matrix.put("passed", failures.isEmpty());
        matrix.put("portableSourceHash", portableSourceHash);
        matrix.put("targets", TARGETS);
        matrix.put("results", results);
        matrix.put("failures", failures);
        return matrix;
    }

    private static Map<?, ?> singleResult(Path report) throws IOException {
        Map<?, ?> document = object(new JsonSlurper().parseText(readBounded(report, MAX_REPORT_BYTES)), "report");
        require(number(document.get("schemaVersion")) == 1, "Unsupported report schema in " + report);
        require(document.get("results") instanceof List<?> values && values.size() == 1,
                "Expected exactly one result in " + report);
        return object(((List<?>) document.get("results")).get(0), "result");
    }

    private static void requirePass(Map<?, ?> row, List<String> flags, String lane) {
        require(Boolean.TRUE.equals(row.get("passed")), lane + " passed is not true");
        require("".equals(row.get("failure")), lane + " includes failure text");
        for (String flag : flags) require(Boolean.TRUE.equals(row.get(flag)), lane + ' ' + flag + " is not true");
    }

    private static Map<?, ?> object(Object value, String name) {
        require(value instanceof Map<?, ?>, name + " is not an object");
        return (Map<?, ?>) value;
    }

    private static String string(Object value, String name) {
        require(value instanceof String && !((String) value).isBlank(), name + " is missing");
        return (String) value;
    }

    private static int number(Object value) {
        require(value instanceof Number, "Expected a numeric value");
        return ((Number) value).intValue();
    }

    private static String readBounded(Path path, int maximum) throws IOException {
        require(Files.isRegularFile(path) && !Files.isSymbolicLink(path), "Missing or unsafe evidence " + path);
        require(Files.size(path) <= maximum, "Oversized evidence " + path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static void requireContains(String text, String marker, String source) {
        require(text.contains(marker), source + " lacks checkpoint " + marker);
    }

    private static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
