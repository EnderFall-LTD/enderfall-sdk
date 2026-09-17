package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

class RuntimeEntrypointEmitterTest {
    @Test void emitsAllTargetsDeterministicallyOnlyWhenDeclared() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            var target = TargetCatalog.standard().require(id);
            assertTrue(RuntimeEntrypointEmitter.emitIfPresent(target, Set.of()).isEmpty());
            var first = RuntimeEntrypointEmitter.emitIfPresent(target, paths(id));
            assertEquals(1, first.size());
            assertArrayEquals(first.get(0).content(), RuntimeEntrypointEmitter.emitIfPresent(target, paths(id)).get(0).content());
        }
        assertEquals(text("1.20.1-forge"), text("1.20.1-neoforge"));
    }

    @Test void retainsLoaderHooksWithoutInitializingConsumers() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            String source = text(id);
            boolean fabric = id.endsWith("-fabric");
            boolean legacy = id.equals("1.20.1-forge") || id.equals("1.20.1-neoforge");
            assertEquals(fabric, source.contains("implements ModInitializer"));
            assertEquals(fabric, source.contains("public void onInitialize()"));
            assertEquals(!fabric, source.contains("@Mod(\"enderfall_sdk\")"));
            assertEquals(legacy, source.contains("net.minecraftforge.fml.common.Mod"));
            assertEquals(!fabric && !legacy, source.contains("net.neoforged.fml.common.Mod"));
            assertFalse(source.contains("RuntimeModBootstrap.initialize"));
            assertFalse(source.contains("client."));
            assertTrue(source.contains("Consumer bootstrap classes initialize their own isolated runtime contexts."));
        }
    }

    @Test void rejectsUnreviewedTargets() {
        TargetSpec base = TargetCatalog.standard().require("26.2-neoforge");
        TargetSpec changed = new TargetSpec(base.id(), base.minecraftVersion(), base.loader(), base.javaVersion(),
                "unreviewed", base.platformApiVersion(), base.loaderAbi(), base.minecraftAbi(), base.mappingAbi(),
                base.recipeAbi(), base.networkAbi(), base.menuAbi());
        assertThrows(BridgeGenerationException.class, () -> RuntimeEntrypointEmitter.emitIfPresent(changed, paths(base.id())));
    }

    private static Set<String> paths(String id) {
        return Set.of("uk/co/enderfall/sdk/runtime/" + (id.endsWith("-fabric")
                ? "fabric/v1_21_4/EnderfallFabricRuntime.java" : "neoforge/v1_21_4/EnderfallNeoForgeRuntime.java"));
    }
    private static String text(String id) throws BridgeGenerationException {
        return new String(RuntimeEntrypointEmitter.emitIfPresent(TargetCatalog.standard().require(id), paths(id)).get(0).content(), StandardCharsets.UTF_8);
    }
}
