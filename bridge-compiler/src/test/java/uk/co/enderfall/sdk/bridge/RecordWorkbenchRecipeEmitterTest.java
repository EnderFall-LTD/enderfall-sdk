package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

class RecordWorkbenchRecipeEmitterTest {
    private static final List<String> TARGETS = List.of("26.2-fabric", "26.2-neoforge");

    @Test void selectsOnlyReviewedRecordTargetsDeterministically() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            var output = RecordWorkbenchRecipeEmitter.emitIfPresent(target(id), paths(id));
            assertEquals(TARGETS.contains(id) ? 1 : 0, output.size());
            if (output.isEmpty()) continue;
            assertArrayEquals(output.get(0).content(),
                    RecordWorkbenchRecipeEmitter.emitIfPresent(target(id), paths(id)).get(0).content());
            assertTrue(output.get(0).relativePath().endsWith(id.endsWith("-fabric")
                    ? "Fabric26WorkbenchRecipe.java" : "NeoForge26WorkbenchRecipe.java"));
        }
    }

    @Test void absentAndPartialFeatureDeclarationsFailSafely() throws Exception {
        for (String id : TARGETS) {
            assertTrue(RecordWorkbenchRecipeEmitter.emitIfPresent(target(id), Set.of()).isEmpty());
            var required = paths(id).stream().sorted().toList();
            for (int mask = 1; mask < 7; mask++) {
                var selected = new java.util.HashSet<String>();
                for (int bit = 0; bit < 3; bit++) if ((mask & (1 << bit)) != 0) selected.add(required.get(bit));
                assertThrows(BridgeGenerationException.class,
                        () -> RecordWorkbenchRecipeEmitter.emitIfPresent(target(id), selected));
            }
        }
    }

    @Test void fabricDefersBindingResolutionUntilDecodeOrJsonConstruction() throws Exception {
        String source = text(TARGETS.get(0));
        assertTrue(source.contains("new RecipeSerializer<>(recipeCodec(binding), recipeStreamCodec(binding))"));
        assertTrue(source.contains("new FabricWorkbenchRecipe(binding.get(), ingredients, result)"));
        int decode = source.indexOf("public FabricWorkbenchRecipe decode(");
        int resolve = source.indexOf("FabricRecipeBinding resolved = binding.get();");
        int read = source.indexOf("int size = buffer.readVarInt();");
        assertTrue(decode >= 0 && resolve > decode && read > resolve);
        assertTrue(source.contains("new FabricWorkbenchRecipe(resolved, ingredients, ItemStackTemplate.STREAM_CODEC.decode(buffer))"));
    }

    @Test void neoForgeRetainsDirectFactoriesAndCombinedInputDeclaration() throws Exception {
        String source = text(TARGETS.get(1));
        assertTrue(source.contains("codec(NeoForge26RecipeBinding binding)"));
        assertTrue(source.contains("return binding.serializer().get();"));
        assertTrue(source.contains("return binding.type().get();"));
        assertFalse(source.contains("binding.get()"));
        assertTrue(source.contains("record NeoForge26WorkbenchInput(List<ItemStack> items) implements RecipeInput"));
        assertTrue(source.contains("items = List.copyOf(items);"));
        assertFalse(text(TARGETS.get(0)).contains("record FabricWorkbenchInput"));
    }

    @Test void exactWireSizeGuardPrecedesAllocationAndCountsAreBounded() throws Exception {
        for (String id : TARGETS) {
            String source = text(id);
            int read = source.indexOf("int size = buffer.readVarInt();");
            int guard = source.indexOf("if (size != ", read);
            int allocation = source.indexOf("ArrayList<>(size)", read);
            assertTrue(read >= 0 && guard > read && allocation > guard);
            assertTrue(source.contains("if (count < 1 || count > 64)"));
            assertTrue(source.contains("Codec.intRange(1, 64).optionalFieldOf(\"count\", 1)"));
        }
    }

    @Test void usesTemplateResultsAndPreservesWireOrder() throws Exception {
        for (String id : TARGETS) {
            String source = text(id);
            assertTrue(source.contains("private final ItemStackTemplate result;"));
            assertTrue(source.contains("return result.create();"));
            assertFalse(source.contains("HolderLookup"));
            assertFalse(source.contains("ItemStack.STREAM_CODEC"));
            int size = source.indexOf("buffer.writeVarInt(recipe.ingredients.size());");
            int entries = source.indexOf("recipe.ingredients.forEach(entry -> Entry.STREAM_CODEC.encode(buffer, entry));", size);
            int result = source.indexOf("ItemStackTemplate.STREAM_CODEC.encode(buffer, recipe.result);", entries);
            assertTrue(size >= 0 && entries > size && result > entries);
        }
    }

    @Test void preservesCountedMatchingCopiesAndGroupPolicies() throws Exception {
        for (String id : TARGETS) {
            String source = text(id);
            assertTrue(source.contains("this.ingredients = List.copyOf(ingredients);"));
            assertTrue(source.contains("if (input.size() != ingredients.size())"));
            assertTrue(source.contains("!expected.ingredient().test(actual) || actual.getCount() < expected.count()"));
            assertTrue(source.contains(id.endsWith("-fabric") ? "return binding.type().toString();" : "return binding.id().toString();"));
        }
    }

    @Test void rejectsUnreviewedTargetChanges() {
        TargetSpec base = target(TARGETS.get(0));
        TargetSpec changed = new TargetSpec(base.id(), base.minecraftVersion(), base.loader(), base.javaVersion(),
                "unreviewed", base.platformApiVersion(), base.loaderAbi(), base.minecraftAbi(), base.mappingAbi(),
                base.recipeAbi(), base.networkAbi(), base.menuAbi());
        assertThrows(BridgeGenerationException.class,
                () -> RecordWorkbenchRecipeEmitter.emitIfPresent(changed, paths(base.id())));
    }

    private static TargetSpec target(String id) { return TargetCatalog.standard().require(id); }
    private static Set<String> paths(String id) {
        boolean fabric = id.endsWith("-fabric");
        String root = "uk/co/enderfall/sdk/runtime/" + (fabric ? "fabric" : "neoforge")
                + "/v1_21_4/" + (fabric ? "Fabric" : "NeoForge");
        return Set.of(root + "WorkbenchRecipe.java", root + "RecipeBinding.java", root + "WorkbenchInput.java");
    }
    private static String text(String id) throws BridgeGenerationException {
        return new String(RecordWorkbenchRecipeEmitter.emitIfPresent(target(id), paths(id)).get(0).content(),
                StandardCharsets.UTF_8);
    }
}
