package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

class RemainingClientHooksEmitterTest {
    private static Set<String> paths(boolean fabric) {
        String root = fabric ? "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/Fabric"
                : "uk/co/enderfall/sdk/runtime/neoforge/v1_21_4/NeoForge";
        return Set.of(root + "ClientHooks.java", root + "WorkbenchMenu.java", root + "WorkbenchBinding.java",
                root + "WorkbenchScreen.java", root + "PortableMenuScreen.java", root + "RawPayload.java");
    }

    private static List<RuntimeSource> emit(TargetSpec target, Set<String> paths) throws BridgeGenerationException {
        return target.loaderAbi() == LoaderAbi.FABRIC ? FabricClientHooksEmitter.emitIfPresent(target, paths)
                : LegacyClientHooksEmitter.emitIfPresent(target, paths);
    }

    @Test void emitsOnlyReviewedTargetsAndPreservesBinaryNamesDeterministically() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            var target = TargetCatalog.standard().require(id);
            boolean fabric = target.loaderAbi() == LoaderAbi.FABRIC;
            var output = emit(target, paths(fabric));
            assertEquals(target.loaderAbi() == LoaderAbi.MODERN_NEOFORGE ? 0 : 1, output.size());
            if (output.isEmpty()) continue;
            assertArrayEquals(output.get(0).content(), emit(target, paths(fabric)).get(0).content());
            String source = new String(output.get(0).content(), StandardCharsets.UTF_8);
            assertTrue(source.contains("final class " + (fabric ? "Fabric" : "LegacyForge") + "ClientHooks"));
            assertTrue(source.indexOf("shouldStopSmokeClient()") > source.indexOf("TickEvent.Phase.END"));
            assertTrue(source.contains("PortableMenuScreen.close(sessionId);"));
        }
        assertArrayEquals(emit(TargetCatalog.standard().require("1.20.1-forge"), paths(false)).get(0).content(),
                emit(TargetCatalog.standard().require("1.20.1-neoforge"), paths(false)).get(0).content());
    }

    @Test void rejectsMissingDependenciesButDoesNotInventUnselectedHooks() throws Exception {
        for (String id : List.of("1.20.1-fabric", "1.21.4-fabric", "1.20.1-forge", "1.20.1-neoforge")) {
            var target = TargetCatalog.standard().require(id);
            var all = paths(target.loaderAbi() == LoaderAbi.FABRIC);
            for (String missing : all) {
                var selected = new HashSet<>(all);
                selected.remove(missing);
                if (missing.endsWith("ClientHooks.java")) assertTrue(emit(target, selected).isEmpty());
                else if (missing.endsWith("WorkbenchScreen.java") || missing.endsWith("PortableMenuScreen.java"))
                    assertThrows(BridgeGenerationException.class, () -> emit(target, selected));
            }
        }
    }

    @Test void legacyFabricBoundsAllocationAndDispatchesReceiversOnClientThread() throws Exception {
        String source = new String(emit(TargetCatalog.standard().require("1.20.1-fabric"), paths(true)).get(0).content(), StandardCharsets.UTF_8);
        assertTrue(source.indexOf("length > maximumBytes") < source.indexOf("new byte[length]"));
        assertTrue(source.contains("client.execute(() -> receiver.receive"));
        assertTrue(source.contains("ClientPlayNetworking.send(channel, buffer)"));
        assertTrue(source.contains("Duplicate clientbound payload"));
        assertFalse(source.contains("CustomPacketPayload"));
    }

    @Test void legacyFmlRetainsConnectionGuardsAndPingBeforeConnect() throws Exception {
        String source = new String(emit(TargetCatalog.standard().require("1.20.1-neoforge"), paths(false)).get(0).content(), StandardCharsets.UTF_8);
        assertTrue(source.indexOf("connection == null") < source.indexOf("channel.sendToServer(payload)"));
        assertTrue(source.indexOf("channel.isRemotePresent") < source.indexOf("channel.sendToServer(payload)"));
        assertTrue(source.contains("AUTOMATIC_CONNECTION_INSTALLED.compareAndSet(false, true)"));
        assertTrue(source.contains("SMOKE_LOOPBACK_HOST.equals(host)"));
        assertTrue(source.contains("port < 1 || port > 65_535"));
        assertTrue(source.contains("server.ping >= 0L && server.forgeData != null"));
        assertTrue(source.indexOf("server.forgeData != null") < source.indexOf("ConnectScreen.startConnecting"));
    }

    @Test void rejectsUnreviewedTargetChanges() {
        for (String id : List.of("1.21.4-fabric", "1.20.1-forge")) {
            var base = TargetCatalog.standard().require(id);
            var changed = new TargetSpec(base.id(), base.minecraftVersion(), base.loader(), base.javaVersion(),
                    "unreviewed", base.platformApiVersion(), base.loaderAbi(), base.minecraftAbi(), base.mappingAbi(),
                    base.recipeAbi(), base.networkAbi(), base.menuAbi());
            assertThrows(BridgeGenerationException.class, () -> emit(changed, paths(id.endsWith("fabric"))));
        }
    }
}
