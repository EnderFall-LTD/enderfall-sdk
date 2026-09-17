package uk.co.enderfall.sdk.harness;

import static org.junit.jupiter.api.Assertions.*;
import groovy.json.JsonOutput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;

class GameplayMatrixTest {
    @TempDir Path sdk;
    private int reportNumber;

    @Test void acceptsCompleteMatrixWithLogsAndOneSourceHash() throws Exception {
        Map<String, Object> result = audit(report(allTargets()));
        assertEquals(true, result.get("passed"));
        assertEquals("a".repeat(64), result.get("portableSourceHash"));
        assertEquals(9, ((List<?>) result.get("results")).size());
        assertEquals(List.of(), result.get("failures"));
    }

    @Test void requiresEveryCatalogTarget() throws Exception {
        List<Map<String, Object>> rows = allTargets();
        rows.remove(0);
        assertFailed(audit(report(rows)), "Missing target");
    }

    @Test void rejectsAnEmptySelection() {
        assertFailed(audit(), "No input reports");
    }

    @Test void latestFailedAttemptCannotBeHiddenByAnOldPass() throws Exception {
        List<Map<String, Object>> rows = allTargets();
        Path original = report(rows);
        Map<String, Object> failed = new LinkedHashMap<>(rows.get(0));
        failed.put("passed", false);
        Path retry = report(List.of(failed));
        assertFailed(audit(original, retry), "passed is not true");
    }

    @Test void successfulExplicitRetryCanReplaceEarlierFailure() throws Exception {
        List<Map<String, Object>> rows = allTargets();
        Map<String, Object> success = new LinkedHashMap<>(rows.get(0));
        rows.get(0).put("passed", false);
        Path original = report(rows);
        Path retry = report(List.of(success));
        assertEquals(true, audit(original, retry).get("passed"));
    }

    @Test void refusesDuplicateTargetsWithinOneReport() throws Exception {
        List<Map<String, Object>> rows = allTargets();
        rows.add(rows.get(0));
        assertFailed(audit(report(rows)), "Duplicate target");
    }

    @Test void refusesRepeatedReportFiles() throws Exception {
        Path report = report(allTargets());
        assertFailed(audit(report, report), "Repeated report file");
    }

    @Test void rejectsInconsistentPortableSources() throws Exception {
        List<Map<String, Object>> rows = allTargets();
        rows.get(1).put("portableSourceHash", "b".repeat(64));
        assertFailed(audit(report(rows)), "Portable sources differ");
    }

    @Test void rejectsMalformedSourceHash() throws Exception {
        List<Map<String, Object>> rows = allTargets();
        rows.get(0).put("portableSourceHash", "looks-like-a-hash");
        assertFailed(audit(report(rows)), "Invalid portable source hash");
    }

    @Test void checksActualBooleansNotTruthyStrings() throws Exception {
        List<Map<String, Object>> rows = allTargets();
        rows.get(0).put("serverReady", "true");
        assertFailed(audit(report(rows)), "serverReady is not true");
    }

    @Test void requiresCleanNumericExitCodes() throws Exception {
        List<Map<String, Object>> rows = allTargets();
        rows.get(0).put("clientExitCode", "0");
        assertFailed(audit(report(rows)), "exit with code 0");
    }

    @Test void refusesFailureTextEvenWhenPassedIsTrue() throws Exception {
        List<Map<String, Object>> rows = allTargets();
        rows.get(0).put("failure", "Unexpected callback");
        assertFailed(audit(report(rows)), "includes a failure");
    }

    @Test void requiresAllIndependentChecks() throws Exception {
        List<Map<String, Object>> rows = allTargets();
        Map<String, Boolean> incomplete = new LinkedHashMap<>();
        new GameplayEvidence("unused").snapshot().keySet().forEach(key -> incomplete.put(key, true));
        incomplete.remove("shiftCraft");
        rows.get(0).put("gameplayChecks", incomplete);
        assertFailed(audit(report(rows)), "Missing or unrecognized gameplay checks");
    }

    @Test void requiresEveryCheckToBeTrue() throws Exception {
        List<Map<String, Object>> rows = allTargets();
        Map<String, Boolean> checks = new LinkedHashMap<>();
        new GameplayEvidence("unused").snapshot().keySet().forEach(key -> checks.put(key, true));
        checks.put("shiftCraft", false);
        rows.get(0).put("gameplayChecks", checks);
        assertFailed(audit(report(rows)), "gameplay check did not pass");
    }

    @Test void historicalNineCheckReportsCannotSatisfyExpandedInventoryCoverage() throws Exception {
        List<Map<String, Object>> rows = allTargets();
        Map<String, Boolean> oldChecks = new LinkedHashMap<>();
        new GameplayEvidence("unused").snapshot().keySet().forEach(key -> oldChecks.put(key, true));
        oldChecks.remove("fullInventoryRejected");
        oldChecks.remove("fullInventoryUnchanged");
        rows.get(0).put("gameplayChecks", oldChecks);
        assertFailed(audit(report(rows)), "Missing or unrecognized gameplay checks");
    }

    @Test void verifiesLogCheckpointsInsteadOfTrustingReportFlags() throws Exception {
        List<Map<String, Object>> rows = allTargets();
        Files.writeString(sdk.resolve((String) rows.get(0).get("clientLog")), "Process exited normally\n");
        assertFailed(audit(report(rows)), "Log lacks checkpoint");
    }

    @Test void failureMarkerInvalidatesEvenAnOtherwiseCompleteLog() throws Exception {
        List<Map<String, Object>> rows = allTargets();
        Map<String, Object> row = rows.get(0);
        Path log = sdk.resolve((String) row.get("serverLog"));
        Files.writeString(log, Files.readString(log) + "ENDERFALL_GAMEPLAY_FAILED " + row.get("target") + " bad\n");
        assertFailed(audit(report(rows)), "Log contains a gameplay failure");
    }

    @Test void targetPrefixCannotSubstituteForActualCheckpoint() throws Exception {
        List<Map<String, Object>> rows = allTargets();
        Map<String, Object> row = rows.get(0);
        Path log = sdk.resolve((String) row.get("clientLog"));
        Files.writeString(log, Files.readString(log).replace((String) row.get("target"), row.get("target") + "-other"));
        assertFailed(audit(report(rows)), "Log lacks checkpoint");
    }

    @Test void refusesLogPathsOutsideBuild() throws Exception {
        List<Map<String, Object>> rows = allTargets();
        rows.get(0).put("clientLog", "build/../outside.log");
        assertFailed(audit(report(rows)), "Log path escapes");
    }

    @Test void rejectsUnknownTargetsAndSchema() throws Exception {
        List<Map<String, Object>> rows = allTargets();
        rows.get(0).put("target", "26.3-fabric");
        assertFailed(audit(report(rows)), "Unknown target");
        Path invalid = sdk.resolve("build/bad-schema.json");
        Files.writeString(invalid, "{\"schemaVersion\":2,\"results\":[]}");
        assertFailed(audit(invalid), "Unsupported report schema");
    }

    @Test void rejectsMalformedAndOversizedReports() throws Exception {
        Files.createDirectories(sdk.resolve("build"));
        Path malformed = sdk.resolve("build/malformed.json");
        Files.writeString(malformed, "{broken");
        assertFailed(audit(malformed), "Report");
        Path oversized = sdk.resolve("build/oversized.json");
        Files.writeString(oversized, " ".repeat(1_048_577));
        assertFailed(audit(oversized), "oversized evidence");
    }

    @Test void commandWritesFailedReportAndDoesNotClobberInputs() throws Exception {
        Path input = report(List.of(allTargets().get(0)));
        Path output = sdk.resolve("build/matrix.json");
        assertThrows(IllegalStateException.class, () -> GameplayMatrixMain.main(
                new String[] {sdk.toString(), output.toString(), input.toString()}));
        assertTrue(Files.readString(output).contains("\"passed\": false"));
        String original = Files.readString(input);
        assertThrows(IllegalArgumentException.class, () -> GameplayMatrixMain.main(
                new String[] {sdk.toString(), input.toString(), input.toString()}));
        assertEquals(original, Files.readString(input));
    }

    @Test void malformedUtf8AndMissingReportsFailTheAudit() throws Exception {
        Files.createDirectories(sdk.resolve("build"));
        Path invalid = sdk.resolve("build/invalid-utf8.json");
        Files.write(invalid, new byte[] {(byte) 0xc3, 0x28});
        assertFailed(audit(invalid), "Report");
        assertFailed(audit(sdk.resolve("build/missing.json")), "Report");
    }

    @Test void missingAndOversizedLogsCannotPass() throws Exception {
        List<Map<String, Object>> rows = allTargets();
        Path log = sdk.resolve((String) rows.get(0).get("serverLog"));
        Files.delete(log);
        assertFailed(audit(report(rows)), "1.20.1-fabric");
        try (var sparseLog = new java.io.RandomAccessFile(log.toFile(), "rw")) {
            sparseLog.setLength(16 * 1_048_576L + 1);
        }
        assertFailed(audit(report(rows)), "oversized evidence");
    }

    @Test void commandRefusesAnOutputOutsideBuildBeforeWritingAnything() throws Exception {
        Path input = report(allTargets());
        Path output = sdk.resolve("unrelated/matrix.json");
        assertThrows(IllegalArgumentException.class, () -> GameplayMatrixMain.main(
                new String[] {sdk.toString(), output.toString(), input.toString()}));
        assertFalse(Files.exists(output.getParent()));
    }

    @Test void aMissingInputReplacesAnOldSummaryWithFailure() throws Exception {
        Files.createDirectories(sdk.resolve("build"));
        Path output = sdk.resolve("build/matrix.json");
        Files.writeString(output, "{\"passed\":true}");
        assertThrows(IllegalStateException.class, () -> GameplayMatrixMain.main(new String[] {
                sdk.toString(), output.toString(), sdk.resolve("missing.json").toString()}));
        assertTrue(Files.readString(output).contains("\"passed\": false"));
    }

    private List<Map<String, Object>> allTargets() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String target : TargetCatalog.standard().targetIds().stream().sorted().toList()) {
            GameplayEvidence evidence = new GameplayEvidence(target);
            Map<String, Boolean> checks = new LinkedHashMap<>();
            evidence.snapshot().keySet().forEach(key -> checks.put(key, true));
            String serverPath = "build/logs/" + target + ".server.log";
            String clientPath = "build/logs/" + target + ".client.log";
            Files.createDirectories(sdk.resolve("build/logs"));
            Files.writeString(sdk.resolve(serverPath), String.join("\n", evidence.serverMarkers().keySet())
                    + "\nLifecycle SERVER_STARTED on " + target
                    + "\nENDERFALL_CONNECTION_SERVER_COMPLETE " + target + "\n");
            Files.writeString(sdk.resolve(clientPath), String.join("\n", evidence.clientMarkers().keySet())
                    + "\nENDERFALL_CONNECTION_CLIENT_COMPLETE " + target
                    + "\nENDERFALL_MENU_CLIENT_OPEN " + target + "\n");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("target", target);
            for (String flag : List.of("passed", "serverReady", "serverRoundTripComplete",
                    "clientRoundTripComplete", "menuOpened")) row.put(flag, true);
            row.put("serverExitCode", 0);
            row.put("clientExitCode", 0);
            row.put("failure", "");
            row.put("portableSourceHash", "a".repeat(64));
            row.put("gameplayChecks", checks);
            row.put("serverLog", serverPath);
            row.put("clientLog", clientPath);
            rows.add(row);
        }
        return rows;
    }

    private Path report(List<Map<String, Object>> rows) throws Exception {
        Files.createDirectories(sdk.resolve("build"));
        Path path = sdk.resolve("build/report-" + reportNumber++ + ".json");
        Files.writeString(path, JsonOutput.toJson(Map.of("schemaVersion", 1, "results", rows)));
        return path;
    }

    private Map<String, Object> audit(Path... paths) { return GameplayMatrixMain.audit(sdk, List.of(paths)); }

    private static void assertFailed(Map<String, Object> result, String message) {
        assertEquals(false, result.get("passed"));
        assertTrue(result.get("failures").toString().contains(message), result.toString());
    }
}
