package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

class ConsumerBootstrapEmitterTest {
    @Test void emitsAllTargetsDeterministicallyAndPreservesLegacyBinaryNames() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            var target = TargetCatalog.standard().require(id);
            var first = ConsumerBootstrapEmitter.emitIfPresent(target, paths(id));
            assertEquals(1, first.size());
            assertArrayEquals(first.get(0).content(), ConsumerBootstrapEmitter.emitIfPresent(target, paths(id)).get(0).content());
            assertEquals(root(id) + "ConsumerBootstrap.java", first.get(0).canonicalRelativePath());
            if (legacy(id)) assertTrue(first.get(0).relativePath().endsWith("forge/v1_20_1/LegacyForgeConsumerBootstrap.java"));
        }
        assertEquals(text("1.20.1-forge"), text("1.20.1-neoforge"));
    }

    @Test void requiresAdapterOnlyWhenBootstrapIsSelected() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            var target = TargetCatalog.standard().require(id);
            assertTrue(ConsumerBootstrapEmitter.emitIfPresent(target, Set.of()).isEmpty());
            assertTrue(ConsumerBootstrapEmitter.emitIfPresent(target, Set.of(root(id) + "PlatformAdapter.java")).isEmpty());
            assertThrows(BridgeGenerationException.class, () -> ConsumerBootstrapEmitter.emitIfPresent(target,
                    Set.of(root(id) + "ConsumerBootstrap.java")));
        }
    }

    @Test void preservesClassLoaderAndEventBusSelection() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            String source = text(id);
            boolean fabric = id.endsWith("-fabric");
            assertEquals(fabric, source.contains("Thread.currentThread().getContextClassLoader()"));
            assertEquals(!fabric, source.contains("ClassLoader consumerClassLoader"));
            assertEquals(!fabric && !legacy(id), source.contains("IEventBus modBus"));
            assertEquals(legacy(id), source.contains("FMLJavaModLoadingContext.get().getModEventBus()"));
            assertEquals(legacy(id), source.contains("@SuppressWarnings({\"deprecation\", \"removal\"})"));
        }
    }

    @Test void retainsInitializationDuplicateGuardAndAttachOrdering() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            String source = text(id);
            int initialize = source.indexOf("RuntimeModBootstrap.initialize(");
            int remember = source.indexOf("CONTEXTS.putIfAbsent(modId, context)");
            int attach = source.indexOf("adapter.attach(context);");
            assertTrue(initialize >= 0 && remember > initialize && attach > remember);
            assertTrue(source.contains("new ConcurrentHashMap<>()"));
            assertTrue(source.contains("EnderFall consumer initialized twice"));
            assertEquals(id.endsWith("-fabric"), source.contains("static RuntimeModContext context(String modId)"));
            if (id.endsWith("-fabric")) assertTrue(source.contains("EnderFall consumer has not initialized"));
        }
    }

    @Test void rejectsUnreviewedTargetChanges() {
        TargetSpec base = TargetCatalog.standard().require("26.2-neoforge");
        TargetSpec changed = new TargetSpec(base.id(), base.minecraftVersion(), base.loader(), base.javaVersion(),
                "unreviewed", base.platformApiVersion(), base.loaderAbi(), base.minecraftAbi(), base.mappingAbi(),
                base.recipeAbi(), base.networkAbi(), base.menuAbi());
        assertThrows(BridgeGenerationException.class, () -> ConsumerBootstrapEmitter.emitIfPresent(changed, paths(base.id())));
    }

    private static boolean legacy(String id) { return id.equals("1.20.1-forge") || id.equals("1.20.1-neoforge"); }
    private static String root(String id) {
        boolean fabric = id.endsWith("-fabric");
        return "uk/co/enderfall/sdk/runtime/" + (fabric ? "fabric/v1_21_4/Fabric" : "neoforge/v1_21_4/NeoForge");
    }
    private static Set<String> paths(String id) { return Set.of(root(id) + "ConsumerBootstrap.java", root(id) + "PlatformAdapter.java"); }
    private static String text(String id) throws BridgeGenerationException {
        return new String(ConsumerBootstrapEmitter.emitIfPresent(TargetCatalog.standard().require(id), paths(id)).get(0).content(), StandardCharsets.UTF_8);
    }
}
