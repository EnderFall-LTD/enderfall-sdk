package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

class CodecWorkbenchRecipeEmitterTest {
    private static final List<String> TARGETS = List.of(
            "1.21.1-fabric", "1.21.4-fabric", "1.21.1-neoforge", "1.21.4-neoforge");

    @Test void generatesOnlyMigratedAbisAndDoesSoDeterministically() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            var output = CodecWorkbenchRecipeEmitter.emitIfPresent(target(id), paths(id));
            assertEquals(TARGETS.contains(id) ? 1 : 0, output.size());
            if (output.isEmpty()) continue;
            assertArrayEquals(output.get(0).content(),
                    CodecWorkbenchRecipeEmitter.emitIfPresent(target(id), paths(id)).get(0).content());
            assertTrue(output.get(0).relativePath().endsWith(id.startsWith("1.21.1")
                    ? "1211WorkbenchRecipe.java" : "WorkbenchRecipe.java"));
            assertFalse(text(id).contains("\r"));
        }
    }

    @Test void absentFeatureDoesNotGenerateStrayRecipeClasses() throws Exception {
        for (String id : TARGETS) {
            assertTrue(CodecWorkbenchRecipeEmitter.emitIfPresent(target(id), Set.of("example/Marker.java")).isEmpty());
        }
    }

    @Test void everyPartialFeatureDeclarationFailsClosed() {
        for (String id : TARGETS) {
            var required = paths(id).stream().sorted().toList();
            for (int mask = 1; mask < 7; mask++) {
                var selected = new java.util.HashSet<String>();
                for (int bit = 0; bit < 3; bit++) if ((mask & (1 << bit)) != 0) selected.add(required.get(bit));
                assertThrows(BridgeGenerationException.class,
                        () -> CodecWorkbenchRecipeEmitter.emitIfPresent(target(id), selected));
            }
        }
    }

    @Test void preservesFactoryResolutionAndRegistryHandleTiming() throws Exception {
        for (String id : TARGETS) {
            String source = text(id);
            boolean fabric = id.endsWith("-fabric");
            assertEquals(fabric, source.contains("serializer(java.util.function.Supplier<FabricRecipeBinding> binding)"));
            assertTrue(source.contains(fabric ? "return recipeCodec(binding.get());" : "return recipeCodec(binding);"));
            assertTrue(source.contains(fabric ? "return binding.serializer();" : "return binding.serializer().get();"));
        }
    }

    @Test void rejectsInvalidWireLengthsBeforeAllocationAndBoundsIngredientCounts() throws Exception {
        for (String id : TARGETS) {
            String source = text(id);
            int read = source.indexOf("int size = buffer.readVarInt();");
            int guard = source.indexOf("if (size != binding.inputSlots())", read);
            int allocate = source.indexOf("new ArrayList<>(size)", read);
            assertTrue(read >= 0 && guard > read && allocate > guard);
            assertTrue(source.contains("if (count < 1 || count > 64) throw new IllegalArgumentException"));
            assertTrue(source.contains("Codec.intRange(1, 64).optionalFieldOf(\"count\", 1)"));
            assertTrue(source.contains("ByteBufCodecs.VAR_INT, Entry::count"));
            assertTrue(source.contains("new " + prefix(id) + "WorkbenchRecipe(binding, ingredients, ItemStack.STREAM_CODEC.decode(buffer))"));
        }
    }

    @Test void emitsTheCorrectNativeRecipeInterfaceMethods() throws Exception {
        for (String id : TARGETS) {
            String source = text(id);
            boolean placement = id.startsWith("1.21.4-");
            assertEquals(placement, source.contains("PlacementInfo.NOT_PLACEABLE"));
            assertEquals(placement, source.contains("RecipeBookCategory recipeBookCategory()"));
            assertEquals(!placement, source.contains("canCraftInDimensions(int width, int height)"));
            assertEquals(!placement, source.contains("getResultItem(HolderLookup.Provider registries)"));
            assertEquals(placement, source.contains("@SuppressWarnings(\"deprecation\")"));
        }
    }

    @Test void retainsDefensiveCopiesAndPositionalCountedMatching() throws Exception {
        for (String id : TARGETS) {
            String source = text(id);
            assertTrue(source.contains("this.ingredients = List.copyOf(ingredients);"));
            assertTrue(source.contains("this.result = result.copy();"));
            assertTrue(source.contains("if (input.size() != ingredients.size()) return false;"));
            assertTrue(source.contains("ItemStack actual = input.getItem(slot);"));
            assertTrue(source.contains("!expected.ingredient().test(actual) || actual.getCount() < expected.count()"));
        }
    }

    @Test void rejectsUnreviewedTargetChanges() {
        TargetSpec base = target("1.21.4-fabric");
        TargetSpec changed = new TargetSpec(base.id(), base.minecraftVersion(), base.loader(), base.javaVersion(),
                "0.19.6", base.platformApiVersion(), base.loaderAbi(), base.minecraftAbi(), base.mappingAbi(),
                base.recipeAbi(), base.networkAbi(), base.menuAbi());
        assertThrows(BridgeGenerationException.class,
                () -> CodecWorkbenchRecipeEmitter.emitIfPresent(changed, paths(base.id())));
    }

    private static TargetSpec target(String id) { return TargetCatalog.standard().require(id); }
    private static String prefix(String id) { return id.endsWith("-fabric") ? "Fabric" : "NeoForge"; }
    private static Set<String> paths(String id) {
        String root = "uk/co/enderfall/sdk/runtime/" + (id.endsWith("-fabric") ? "fabric" : "neoforge")
                + "/v1_21_4/" + prefix(id);
        return Set.of(root + "WorkbenchRecipe.java", root + "RecipeBinding.java", root + "WorkbenchInput.java");
    }
    private static String text(String id) throws BridgeGenerationException {
        return new String(CodecWorkbenchRecipeEmitter.emitIfPresent(target(id), paths(id)).get(0).content(),
                StandardCharsets.UTF_8);
    }
}
