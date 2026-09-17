package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

class ClientHooksEmitterTest {
    private static final List<String> TARGETS = List.of("1.21.1-neoforge", "1.21.4-neoforge", "26.2-neoforge");
    private static final String ROOT = "uk/co/enderfall/sdk/runtime/neoforge/v1_21_4/NeoForge";
    private static final Set<String> PATHS = Set.of(ROOT + "ClientHooks.java", ROOT + "WorkbenchBinding.java",
            ROOT + "WorkbenchScreen.java", ROOT + "RawPayload.java", ROOT + "PortableMenuScreen.java");

    @Test void selectsReviewedModernFmlTargetsDeterministically() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            var target = TargetCatalog.standard().require(id);
            var output = ClientHooksEmitter.emitIfPresent(target, PATHS);
            assertEquals(TARGETS.contains(id) ? 1 : 0, output.size());
            if (output.isEmpty()) continue;
            assertArrayEquals(output.get(0).content(), ClientHooksEmitter.emitIfPresent(target, PATHS).get(0).content());
        }
        assertEquals(text("1.21.1-neoforge"), text("1.21.4-neoforge"));
    }

    @Test void rejectsPartialDependenciesWithoutCreatingUnrequestedHooks() throws Exception {
        for (String id : TARGETS) {
            var target = TargetCatalog.standard().require(id);
            var required = PATHS.stream().sorted().toList();
            for (int mask = 0; mask < 31; mask++) {
                var selected = new java.util.HashSet<String>();
                for (int bit = 0; bit < 5; bit++) if ((mask & (1 << bit)) != 0) selected.add(required.get(bit));
                if (selected.contains(ROOT + "ClientHooks.java")) {
                    assertThrows(BridgeGenerationException.class, () -> ClientHooksEmitter.emitIfPresent(target, selected));
                } else assertTrue(ClientHooksEmitter.emitIfPresent(target, selected).isEmpty());
            }
        }
    }

    @Test void retainsNativeRegistrationsTickOrderingAndSmokeShutdown() throws Exception {
        for (String id : TARGETS) {
            String source = text(id);
            assertTrue(source.contains("event.enqueueWork("));
            assertTrue(source.contains("LifecycleEvent.Stage.CLIENT_STARTED"));
            assertTrue(source.contains("LifecycleEvent.Stage.CLIENT_STOPPING"));
            assertTrue(source.contains("TickEvent.Phase.START, tick.get()"));
            assertTrue(source.contains("TickEvent.Phase.END, tick.getAndIncrement()"));
            int tick = source.indexOf("TickEvent.Phase.END");
            assertTrue(source.indexOf("IntegrationTestControl.shouldStopSmokeClient()") > tick);
            assertTrue(source.contains("Minecraft.getInstance().stop();"));
            boolean newer = id.startsWith("26.2");
            assertTrue(source.contains(newer ? "NeoForge26WorkbenchScreen::new" : "NeoForgeWorkbenchScreen::new"));
            assertEquals(newer, source.contains("ClientPacketDistributor.sendToServer(payload);"));
            assertTrue(source.contains("NeoForgePortableMenuScreen.close(sessionId);"));
        }
    }

    @Test void rejectsUnreviewedTargetChanges() {
        TargetSpec base = TargetCatalog.standard().require(TARGETS.get(0));
        TargetSpec changed = new TargetSpec(base.id(), base.minecraftVersion(), base.loader(), base.javaVersion(),
                "unreviewed", base.platformApiVersion(), base.loaderAbi(), base.minecraftAbi(), base.mappingAbi(),
                base.recipeAbi(), base.networkAbi(), base.menuAbi());
        assertThrows(BridgeGenerationException.class, () -> ClientHooksEmitter.emitIfPresent(changed, PATHS));
    }

    private static String text(String id) throws BridgeGenerationException {
        return new String(ClientHooksEmitter.emitIfPresent(TargetCatalog.standard().require(id), PATHS).get(0).content(), StandardCharsets.UTF_8);
    }
}
