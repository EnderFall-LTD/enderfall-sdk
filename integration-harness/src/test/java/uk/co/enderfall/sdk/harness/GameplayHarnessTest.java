package uk.co.enderfall.sdk.harness;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;

class GameplayHarnessTest {
    @TempDir Path temporary;

    @Test void refusesGameplaySuccessWithNoAssertions() {
        GameplayEvidence evidence = new GameplayEvidence("1.21.4-fabric");
        assertFalse(evidence.passed());
        assertEquals(11, evidence.snapshot().size());
        assertTrue(evidence.snapshot().values().stream().noneMatch(Boolean::booleanValue));
    }

    @Test void everyIndependentAssertionIsRequiredEvenAfterCompletionMessages() {
        for (int omitted = 0; omitted < 11; omitted++) {
            GameplayEvidence evidence = new GameplayEvidence("1.21.4-fabric");
            var latches = new ArrayList<>(evidence.serverMarkers().values());
            latches.addAll(evidence.clientMarkers().values());
            for (int index = 0; index < latches.size(); index++) {
                if (index != omitted) latches.get(index).countDown();
            }
            assertFalse(evidence.passed(), "Missing assertion " + omitted + " incorrectly passed");
        }
    }

    @Test void allServerAndClientAssertionsProduceACompleteSnapshot() {
        GameplayEvidence evidence = new GameplayEvidence("1.20.1-forge");
        evidence.serverMarkers().values().forEach(java.util.concurrent.CountDownLatch::countDown);
        assertFalse(evidence.passed());
        evidence.clientMarkers().values().forEach(java.util.concurrent.CountDownLatch::countDown);
        assertTrue(evidence.passed());
        assertEquals(0, evidence.clientComplete().getCount());
        assertTrue(evidence.snapshot().values().stream().allMatch(Boolean::booleanValue));
    }

    @Test void evidenceMarkersAreTargetSpecificAndSidesAreSeparated() {
        GameplayEvidence evidence = new GameplayEvidence("26.2-neoforge");
        assertTrue(evidence.serverMarkers().keySet().stream().allMatch(key -> key.endsWith(" 26.2-neoforge")));
        assertTrue(evidence.clientMarkers().keySet().stream().allMatch(key -> key.endsWith(" 26.2-neoforge")));
        assertFalse(evidence.serverMarkers().containsKey("ENDERFALL_GAMEPLAY_CLIENT_COMPLETE 26.2-neoforge"));
        assertThrows(UnsupportedOperationException.class, () -> evidence.snapshot().clear());
    }

    @Test void oneDriverCoversEveryReviewedTargetDeterministically() throws Exception {
        for (String target : TargetCatalog.standard().targetIds()) {
            String source = GameplayProbeSources.render(target);
            assertEquals(source, GameplayProbeSources.render(target));
            assertFalse(source.contains("@CLICK_"));
            assertFalse(source.contains("@SCREEN@"));
            assertFalse(source.contains("@PRESS_BUTTON@"));
            assertFalse(source.contains("@ITEM_ID@"));
            assertTrue(source.contains("getCarried().isEmpty()"));
            assertTrue(source.contains("INSUFFICIENT_REJECTED"));
            assertTrue(source.contains("REOPEN_EMPTY"));
            assertTrue(source.contains("FULL_INVENTORY_UNCHANGED"));
            assertTrue(source.contains("inventoryCount(HAMMER) == 36"));
        }
    }

    @Test void driverOnlyVariesByMinecraftInputAbiNotByLoader() throws Exception {
        for (String version : List.of("1.20.1", "1.21.1", "1.21.4", "26.2")) {
            assertEquals(GameplayProbeSources.render(version + "-fabric"),
                    GameplayProbeSources.render(version + "-neoforge"));
        }
        assertEquals(GameplayProbeSources.render("1.20.1-forge"),
                GameplayProbeSources.render("1.20.1-neoforge"));
        assertTrue(GameplayProbeSources.render("26.2-fabric").contains("handleContainerInput"));
        assertTrue(GameplayProbeSources.render("1.20.1-forge").contains("handleInventoryMouseClick"));
        assertTrue(GameplayProbeSources.render("1.20.1-forge").contains(
                "stack.getItemHolder().unwrapKey().orElseThrow().location()"));
    }

    @Test void probeLivesOnlyInNativeTargetRootAndDoesNotRewritePortableSources() throws Exception {
        Path portable = temporary.resolve("src/main/java/example/Mod.java");
        Files.createDirectories(portable.getParent());
        Files.writeString(portable, "package example; public class Mod {}");
        GameplayProbeSources.write(temporary, "1.21.4-fabric");
        assertEquals("package example; public class Mod {}", Files.readString(portable));
        assertTrue(Files.isRegularFile(temporary.resolve(
                "src/target/1.21.4-fabric/java/uk/co/enderfall/sdk/testmod/NativeGameplayClient.java")));
    }

    @Test void rejectsUnknownTargetsBeforeCreatingAnyOutput() {
        assertThrows(IllegalArgumentException.class,
                () -> GameplayProbeSources.write(temporary, "../escaped"));
        assertThrows(IllegalArgumentException.class, () -> GameplayProbeSources.render("26.3-fabric"));
    }

    @Test void preparesClientAssetsOutsideTheGameTimeoutOnEveryTarget() {
        for (String target : TargetCatalog.standard().targetIds()) {
            var client = SameLoaderSmokeMain.gameplayPreparationTasks(target, true);
            var server = SameLoaderSmokeMain.gameplayPreparationTasks(target, false);
            String project = ":enderfallTargets:" + target.replace('.', '_').replace('-', '_');
            assertEquals(List.of("generateData", project + ":classes"), client.subList(0, 2));
            if (target.endsWith("-fabric")) {
                assertEquals(project + ":downloadAssets", client.get(2));
                assertEquals(2, server.size());
            } else {
                assertEquals("prepareClient", client.get(2));
                assertEquals("prepareServer", server.get(2));
            }
        }
    }

    @Test void preparationRejectsUnknownTargets() {
        assertThrows(IllegalArgumentException.class,
                () -> SameLoaderSmokeMain.gameplayPreparationTasks("../outside", true));
    }

    @Test void connectionObserverIsSharedByAllLegacyLoadersAndNeverGeneratedForModernTargets() throws Exception {
        String legacyObserver = null;
        for (String target : TargetCatalog.standard().targetIds()) {
            Path workspace = temporary.resolve(target);
            GameplayProbeSources.write(workspace, target);
            Path observer = workspace.resolve("src/target/" + target
                    + "/java/uk/co/enderfall/sdk/testmod/LegacyConnectionDiagnostics.java");
            assertFalse(GameplayProbeSources.render(target).contains("@CONNECTION_OBSERVER@"));
            if (target.startsWith("1.20.1-")) {
                String contents = Files.readString(observer);
                if (legacyObserver == null) legacyObserver = contents;
                assertEquals(legacyObserver, contents);
                assertTrue(GameplayProbeSources.render(target).contains("LegacyConnectionDiagnostics.install(context)"));
                assertTrue(contents.contains("ENDERFALL_LEGACY_CONNECTION_DIAGNOSTICS"));
                assertTrue(contents.contains("channel.eventLoop().execute"));
                assertTrue(contents.contains("key.interestOps() & SelectionKey.OP_READ"));
                assertFalse(contents.contains("setAutoRead("));
                assertFalse(contents.contains(".setProtocol("));
                assertFalse(contents.contains(".read()"));
            } else {
                assertFalse(Files.exists(observer));
                assertFalse(GameplayProbeSources.render(target).contains("LegacyConnectionDiagnostics"));
            }
        }
    }

    @Test void namedRunReportsCannotOverwriteEarlierEvidence() throws Exception {
        Path report = temporary.resolve("build/reports/generated-bridge-gameplay/runs/attempt-1");
        SameLoaderSmokeMain.prepareReportDirectory(temporary, report);
        Files.writeString(report.resolve("connection.json"), "original evidence");
        assertThrows(java.nio.file.FileAlreadyExistsException.class,
                () -> SameLoaderSmokeMain.prepareReportDirectory(temporary, report));
        assertEquals("original evidence", Files.readString(report.resolve("connection.json")));
        SameLoaderSmokeMain.prepareReportDirectory(temporary, report.resolveSibling("attempt-2"));
    }

    @Test void laterCompletionCannotEraseAnEarlierGameplayFailure() {
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        SameLoaderSmokeMain.recordGameplayFailure("ENDERFALL_GAMEPLAY_CLIENT_COMPLETE 1.20.1-forge", failure);
        assertNull(failure.get());
        SameLoaderSmokeMain.recordGameplayFailure("[ERROR] ENDERFALL_GAMEPLAY_FAILED 1.20.1-forge observer", failure);
        Throwable original = failure.get();
        assertNotNull(original);
        SameLoaderSmokeMain.recordGameplayFailure("ENDERFALL_GAMEPLAY_CLIENT_COMPLETE 1.20.1-forge", failure);
        SameLoaderSmokeMain.recordGameplayFailure("ENDERFALL_GAMEPLAY_FAILED 1.20.1-forge craft", failure);
        assertSame(original, failure.get());
    }

    @Test void reportsStayInsideBuildAndTheLegacyDefaultRemainsReusable() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> SameLoaderSmokeMain.prepareReportDirectory(temporary, temporary.resolve("outside")));
        assertThrows(IllegalArgumentException.class,
                () -> SameLoaderSmokeMain.prepareReportDirectory(temporary, temporary.resolve("build")));
        Path legacy = temporary.resolve("build/reports/generated-bridge-gameplay");
        SameLoaderSmokeMain.prepareReportDirectory(temporary, legacy);
        SameLoaderSmokeMain.prepareReportDirectory(temporary, legacy);
        assertTrue(Files.isDirectory(legacy));
    }
}
