package uk.co.enderfall.sdk.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import groovy.json.JsonOutput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;

class ReleaseMatrixTest {
    private static final String REVISION = "a".repeat(40);
    @TempDir Path temporary;

    @Test void acceptsOnlyCompleteCurrentRevisionEvidence() throws Exception {
        createCompleteEvidence();
        Map<String, Object> result = ReleaseMatrixMain.audit(temporary, REVISION);
        assertEquals(true, result.get("passed"));
        assertEquals(9, ((List<?>) result.get("results")).size());
        assertEquals("b".repeat(64), result.get("portableSourceHash"));
    }

    @Test void rejectsMissingTargetAndStaleRevision() throws Exception {
        createCompleteEvidence();
        Path first;
        try (var paths = Files.list(temporary)) { first = paths.sorted().findFirst().orElseThrow(); }
        Files.writeString(first.resolve("revision.txt"), "c".repeat(40));
        deleteRecursively(temporary.resolve("acceptance-26.2-neoforge"));
        Map<String, Object> result = ReleaseMatrixMain.audit(temporary, REVISION);
        assertEquals(false, result.get("passed"));
        assertTrue(result.get("failures").toString().contains("does not match"));
        assertTrue(result.get("failures").toString().contains("Missing target 26.2-neoforge"));
    }

    @Test void rejectsForgedFlagsWhenLogsLackCheckpoints() throws Exception {
        createCompleteEvidence();
        Path artifact = temporary.resolve("acceptance-1.21.4-fabric");
        Files.writeString(artifact.resolve("client/client/1.21.4-fabric.log"), "BUILD SUCCESSFUL\n");
        Map<String, Object> result = ReleaseMatrixMain.audit(temporary, REVISION);
        assertEquals(false, result.get("passed"));
        assertTrue(result.get("failures").toString().contains("client log lacks checkpoint"));
    }

    @Test void rejectsUnknownOrFalseGameplayChecks() throws Exception {
        createCompleteEvidence();
        Path report = temporary.resolve("acceptance-1.20.1-forge/gameplay/connection.json");
        String content = Files.readString(report).replace("\"commandExecuted\":true",
                "\"commandExecuted\":false");
        Files.writeString(report, content);
        Map<String, Object> result = ReleaseMatrixMain.audit(temporary, REVISION);
        assertEquals(false, result.get("passed"));
        assertTrue(result.get("failures").toString().contains("gameplay check is not true"));
    }

    private void createCompleteEvidence() throws Exception {
        for (String target : TargetCatalog.standard().targetIds()) {
            Path artifact = temporary.resolve("acceptance-" + target);
            Files.createDirectories(artifact);
            Files.writeString(artifact.resolve("revision.txt"), REVISION + "\n");
            Map<String, Object> server = row(target, "serverStarted", "stoppedCleanly");
            server.put("exitCode", 0);
            writeReport(artifact.resolve("server/server.json"), server);
            Path serverLog = artifact.resolve("server/server/" + target + ".log");
            Files.createDirectories(serverLog.getParent());
            Files.writeString(serverLog, "Lifecycle SERVER_STARTED on " + target + "\n"
                    + "ENDERFALL_GAMEPLAY_FOUNDATION_REGISTRATION_READY " + target + "\n"
                    + "ENDERFALL_GAMEPLAY_FOUNDATION_CONFIG_READY " + target + "\n"
                    + "ENDERFALL_GAMEPLAY_FOUNDATION_LISTENER_ISOLATION_READY " + target + "\n");

            Map<String, Object> client = row(target, "lifecycleAndTicksReady");
            client.put("exitCode", 0);
            writeReport(artifact.resolve("client/client.json"), client);
            Path clientLog = artifact.resolve("client/client/" + target + ".log");
            Files.createDirectories(clientLog.getParent());
            Files.writeString(clientLog, "ENDERFALL_CLIENT_SMOKE_READY " + target + "\n"
                    + "ENDERFALL_CLIENT_RESOURCES_RELOADED " + target + " 2\n"
                    + "ENDERFALL_CLIENT_SMOKE_COMPLETE " + target + "\n");

            Map<String, Object> gameplay = row(target, "serverReady", "serverRoundTripComplete",
                    "clientRoundTripComplete", "menuOpened");
            gameplay.put("serverExitCode", 0);
            gameplay.put("clientExitCode", 0);
            gameplay.put("portableSourceHash", "b".repeat(64));
            Map<String, Boolean> checks = new LinkedHashMap<>();
            new GameplayEvidence(target).snapshot().keySet().forEach(key -> checks.put(key, true));
            gameplay.put("gameplayChecks", checks);
            writeReport(artifact.resolve("gameplay/connection.json"), gameplay);
            Path gameplayServer = artifact.resolve("gameplay/connection/" + target + ".server.log");
            Path gameplayClient = artifact.resolve("gameplay/connection/" + target + ".client.log");
            Files.createDirectories(gameplayServer.getParent());
            Files.createDirectories(gameplayClient.getParent());
            Files.writeString(gameplayServer,
                    String.join("\n", new GameplayEvidence(target).serverMarkers().keySet()) + "\n");
            Files.writeString(gameplayClient,
                    String.join("\n", new GameplayEvidence(target).clientMarkers().keySet()) + "\n");
        }
    }

    private static Map<String, Object> row(String target, String... flags) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("target", target);
        row.put("passed", true);
        row.put("failure", "");
        for (String flag : flags) row.put(flag, true);
        return row;
    }

    private static void writeReport(Path path, Map<String, Object> row) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, JsonOutput.toJson(Map.of("schemaVersion", 1, "results", List.of(row))));
    }

    private static void deleteRecursively(Path directory) throws Exception {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
        }
        assertFalse(Files.exists(directory));
    }
}
