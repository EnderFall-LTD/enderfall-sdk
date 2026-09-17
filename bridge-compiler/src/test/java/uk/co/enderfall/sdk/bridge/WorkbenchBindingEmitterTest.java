package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

class WorkbenchBindingEmitterTest {
    @Test void emitsDeterministicallyForEveryReviewedTargetWithoutSourceTemplates() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            var sources = emit(id);
            assertEquals(id.equals("1.20.1-fabric") || id.equals("26.2-neoforge") ? 1 : 2, sources.size());
            assertEquals(sources.size(), sources.stream().map(RuntimeSource::relativePath).distinct().count());
            assertEquals(text(sources), text(emit(id)));
            for (RuntimeSource source : sources) {
                assertTrue(source.relativePath().endsWith("Binding.java"));
                assertFalse(source.relativePath().contains(".."));
                assertFalse(new String(source.content(), StandardCharsets.UTF_8).contains("\r"));
            }
        }
    }

    @Test void registryHandleSemanticsFollowTheLoaderFamily() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            String output = text(emit(id));
            assertTrue(output.contains("int inputSlots"));
            assertTrue(output.contains("PortableWorkbenchDefinition definition"));
            if (id.endsWith("-fabric")) {
                assertFalse(output.contains("Supplier<"));
                assertFalse(output.contains("RegistryObject<"));
            } else if (id.startsWith("1.20.1-")) {
                assertTrue(output.contains("RegistryObject<RecipeType<LegacyForgeWorkbenchRecipe>>"));
                assertTrue(output.contains("RegistryObject<MenuType<LegacyForgeWorkbenchMenu>>"));
                assertFalse(output.contains("Supplier<"));
            } else {
                assertTrue(output.contains("Supplier<RecipeType<"));
                assertTrue(output.contains("Supplier<MenuType<"));
                assertFalse(output.contains("RegistryObject<"));
            }
        }
    }

    @Test void combinedDeclarationsRetainTheirTargetContracts() throws Exception {
        var legacy = emit("1.20.1-fabric");
        assertTrue(legacy.get(0).relativePath().endsWith("Fabric1201WorkbenchBinding.java"));
        assertTrue(text(legacy).contains("record Fabric1201RecipeBinding("));
        var modern = emit("26.2-neoforge");
        assertTrue(modern.get(0).relativePath().endsWith("NeoForge26WorkbenchBinding.java"));
        assertTrue(text(modern).contains("record NeoForge26RecipeBinding(Identifier id,"));
        assertFalse(text(emit("26.2-fabric")).contains("Identifier"));
    }

    @Test void bothLegacyFmlTargetsHaveIdenticalJavaAndPaths() throws Exception {
        var forge = emit("1.20.1-forge");
        var neo = emit("1.20.1-neoforge");
        assertEquals(text(forge), text(neo));
        assertEquals(forge.stream().map(RuntimeSource::relativePath).toList(),
                neo.stream().map(RuntimeSource::relativePath).toList());
    }

    @Test void versionsWithTheSameBindingContractReuseIdenticalDeclarations() throws Exception {
        assertEquals(text(emit("1.21.4-fabric")), text(emit("1.21.1-fabric")));
        assertEquals(text(emit("1.21.4-fabric")), text(emit("26.2-fabric")));
        assertEquals(text(emit("1.21.4-neoforge")), text(emit("1.21.1-neoforge")));
    }

    @Test void rejectsUnreviewedCatalogChanges() {
        TargetSpec target = TargetCatalog.standard().require("1.20.1-forge");
        TargetSpec changed = new TargetSpec(target.id(), target.minecraftVersion(), target.loader(),
                target.javaVersion(), "47.4.24", target.platformApiVersion(), target.loaderAbi(),
                target.minecraftAbi(), target.mappingAbi(), target.recipeAbi(), target.networkAbi(), target.menuAbi());
        assertThrows(BridgeGenerationException.class, () -> WorkbenchBindingEmitter.emit(changed));
    }

    @Test void doesNotAddWorkbenchClassesToAFeaturelessCompilerFixture() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            assertTrue(WorkbenchBindingEmitter.emitIfPresent(TargetCatalog.standard().require(id),
                    java.util.Set.of("example/Marker.java")).isEmpty());
        }
    }

    @Test void partialFeatureDeclarationsFailClosedOnEveryTarget() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            TargetSpec target = TargetCatalog.standard().require(id);
            String prefix = "uk/co/enderfall/sdk/runtime/" + (id.endsWith("-fabric")
                    ? "fabric/v1_21_4/Fabric" : "neoforge/v1_21_4/NeoForge");
            String recipe = prefix + "RecipeBinding.java";
            String menu = prefix + "WorkbenchBinding.java";
            assertThrows(BridgeGenerationException.class,
                    () -> WorkbenchBindingEmitter.emitIfPresent(target, java.util.Set.of(recipe)));
            assertThrows(BridgeGenerationException.class,
                    () -> WorkbenchBindingEmitter.emitIfPresent(target, java.util.Set.of(menu)));
            assertEquals(text(emit(id)), text(WorkbenchBindingEmitter.emitIfPresent(target,
                    java.util.Set.of(recipe, menu))));
        }
    }

    private static List<RuntimeSource> emit(String id) throws BridgeGenerationException {
        return WorkbenchBindingEmitter.emit(TargetCatalog.standard().require(id));
    }

    private static String text(List<RuntimeSource> sources) {
        return sources.stream().map(source -> new String(source.content(), StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
