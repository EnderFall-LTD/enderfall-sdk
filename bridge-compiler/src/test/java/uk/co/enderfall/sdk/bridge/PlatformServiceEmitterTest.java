package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

class PlatformServiceEmitterTest {
    private static Set<String> declarations(TargetSpec target) {
        String root = "uk/co/enderfall/sdk/runtime/" + (target.loaderAbi() == LoaderAbi.FABRIC
                ? "fabric/v1_21_4/Fabric" : "neoforge/v1_21_4/NeoForge");
        Set<String> paths = new HashSet<>();
        for (String suffix : List.of("PlatformAdapter", "PlatformInfo", "ClientHooks", "CommandBridge",
                "RecipeBinding", "WorkbenchBinding", "WorkbenchMenu", "WorkbenchRecipe", "RawPayload")) paths.add(root + suffix + ".java");
        return paths;
    }

    @Test void emitsEveryReviewedTargetDeterministicallyWithNoUnresolvedNames() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            var target = TargetCatalog.standard().require(id);
            var outputs = PlatformServiceEmitter.emitIfPresent(target, declarations(target));
            assertEquals(1, outputs.size());
            assertArrayEquals(outputs.get(0).content(), PlatformServiceEmitter.emitIfPresent(target, declarations(target)).get(0).content());
            assertFalse(source(id).contains("${"));
            var operations = PlatformServiceEmitter.operations(NativePlatformPolicy.forTarget(target));
            assertEquals(operations.size(), new HashSet<>(operations).size());
        }
        assertEquals(source("1.20.1-forge"), source("1.20.1-neoforge"));
        assertEquals(source("1.21.1-neoforge"), source("1.21.4-neoforge"));
    }

    @Test void rejectsIncompletePlatformFeaturesAndDoesNotInventUnrequestedServices() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            var target = TargetCatalog.standard().require(id);
            assertTrue(PlatformServiceEmitter.emitIfPresent(target, Set.of()).isEmpty());
            Set<String> all = declarations(target);
            for (String missing : all) {
                Set<String> selected = new HashSet<>(all);
                selected.remove(missing);
                if (missing.endsWith("PlatformAdapter.java")) assertTrue(PlatformServiceEmitter.emitIfPresent(target, selected).isEmpty());
                else if (!(missing.endsWith("RawPayload.java") && id.startsWith("1.20.1")))
                    assertThrows(BridgeGenerationException.class, () -> PlatformServiceEmitter.emitIfPresent(target, selected));
            }
        }
    }

    @Test void inventoryOperationsShareOneImplementationAcrossEveryPolicy() throws Exception {
        for (var operation : List.of(PlatformOperation.CONNECTED_PLAYERS, PlatformOperation.PLAYER_SNAPSHOT,
                PlatformOperation.COUNT_PLAYER_ITEM, PlatformOperation.CONSUME_PLAYER_ITEMS, PlatformOperation.GIVE_PLAYER_ITEM,
                PlatformOperation.HEAL_PLAYER, PlatformOperation.ADD_PLAYER_EXPERIENCE)) {
            String baseline = PlatformGameplaySources.emit(operation, NativePlatformPolicy.FABRIC_KEYED);
            for (var policy : NativePlatformPolicy.values()) assertEquals(baseline, PlatformGameplaySources.emit(operation, policy));
        }
    }

    @Test void preservesRegistrationAndLifecycleOrderingContracts() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            String source = source(id);
            assertTrue(source.contains("Capability.CONTAINER_MENUS"));
            assertTrue(source.contains("Capability.CUSTOM_RECIPES"));
            assertTrue(source.contains("context.runtimeConfigs().loadServerConfigs()"));
            assertTrue(source.indexOf("loadServerConfigs()") < source.indexOf("publishLifecycle(LifecycleEvent.Stage.SERVER_STARTING)"));
            assertTrue(source.contains("context.runtimeConfigs().unloadServerConfigs()"));
            assertTrue(source.contains("context.runtimeNetworking().connectionOpened("));
            assertTrue(source.contains("context.runtimeNetworking().connectionClosed("));
            if (id.equals("1.20.1-fabric")) {
                assertTrue(source.contains("Workbench references an unregistered recipe type"));
                assertTrue(source.contains("typeReference.set(menuType)"));
            } else {
                assertTrue(source.contains("Duplicate workbench"));
                assertTrue(source.contains("references unregistered recipe type"));
            }
            assertTrue(source.contains("Cannot send to a server from a dedicated server process"));
            assertTrue(source.contains("length > binding.maximumBytes()"));
        }
    }

    @Test void transportFacetsRetainLegacyBoundsAndModernDirectionChecks() throws Exception {
        String fabric = source("1.20.1-fabric");
        assertTrue(fabric.indexOf("length > maximumBytes") < fabric.indexOf("new byte[length]"));
        String fml = source("1.20.1-forge");
        assertTrue(fml.contains("buffer.readByteArray(maximumBytes)"));
        assertTrue(fml.contains("networkContext.enqueueWork("));
        for (String id : List.of("1.21.4-neoforge", "26.2-neoforge")) {
            String source = source(id);
            assertTrue(source.contains("case BIDIRECTIONAL -> registrar.playBidirectional"));
            assertTrue(source.contains("networkContext.flow() == PacketFlow.SERVERBOUND"));
            assertTrue(source.contains("binding.direction()"));
        }
    }

    @Test void rejectsUnreviewedTargetsUnknownNamesAndUnavailableOperations() {
        var base = TargetCatalog.standard().require("1.21.4-fabric");
        var changed = new TargetSpec(base.id(), base.minecraftVersion(), base.loader(), base.javaVersion(),
                "unreviewed", base.platformApiVersion(), base.loaderAbi(), base.minecraftAbi(), base.mappingAbi(),
                base.recipeAbi(), base.networkAbi(), base.menuAbi());
        assertThrows(BridgeGenerationException.class, () -> PlatformServiceEmitter.emitIfPresent(changed, declarations(changed)));
        assertThrows(BridgeGenerationException.class, () -> PlatformServiceEmitter.renderNames("${Unknown}", Map.of()));
        assertThrows(BridgeGenerationException.class, () -> PlatformNetworkingSources.emit(PlatformOperation.READ_PAYLOAD, NativePlatformPolicy.NEOFORGE));
        assertThrows(BridgeGenerationException.class, () -> PlatformGameplaySources.emit(PlatformOperation.REGISTER_ITEM, NativePlatformPolicy.FABRIC_KEYED));
    }

    private static String source(String id) throws BridgeGenerationException {
        var target = TargetCatalog.standard().require(id);
        return new String(PlatformServiceEmitter.emitIfPresent(target, declarations(target)).get(0).content(), StandardCharsets.UTF_8);
    }
}
