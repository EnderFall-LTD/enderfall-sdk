package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

class WorkbenchInputEmitterTest {
    private static final Set<String> PATHS = Set.of("uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricWorkbenchInput.java",
            "uk/co/enderfall/sdk/runtime/neoforge/v1_21_4/NeoForgeWorkbenchInput.java");

    @Test void sharesReadOnlyInputListShapeExceptLegacyAndCombinedRecipeInputs() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            var target = TargetCatalog.standard().require(id);
            var output = WorkbenchInputEmitter.emitIfPresent(target, PATHS);
            assertEquals(id.startsWith("1.20.1") || id.equals("26.2-neoforge") ? 0 : 1, output.size());
            assertTrue(WorkbenchInputEmitter.emitIfPresent(target, Set.of()).isEmpty());
            if (output.isEmpty()) continue;
            assertArrayEquals(output.get(0).content(), WorkbenchInputEmitter.emitIfPresent(target, PATHS).get(0).content());
            String source = new String(output.get(0).content(), StandardCharsets.UTF_8);
            assertTrue(source.contains("items = List.copyOf(items)"));
            assertTrue(source.contains("return items.get(index)"));
            assertTrue(source.contains("return items.size()"));
        }
    }

    @Test void rejectsUnreviewedTarget() {
        var base = TargetCatalog.standard().require("1.21.4-fabric");
        var changed = new TargetSpec(base.id(), base.minecraftVersion(), base.loader(), base.javaVersion(),
                "unreviewed", base.platformApiVersion(), base.loaderAbi(), base.minecraftAbi(), base.mappingAbi(),
                base.recipeAbi(), base.networkAbi(), base.menuAbi());
        assertThrows(BridgeGenerationException.class, () -> WorkbenchInputEmitter.emitIfPresent(changed, PATHS));
    }
}
