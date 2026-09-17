package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

class LegacyWorkbenchRecipeEmitterTest {
    private static final List<String> TARGETS = List.of("1.20.1-fabric", "1.20.1-forge", "1.20.1-neoforge");

    @Test void selectsOnlyLegacyTargetsAndEmitsDeterministically() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            var output = LegacyWorkbenchRecipeEmitter.emitIfPresent(target(id), paths(id));
            assertEquals(TARGETS.contains(id) ? 1 : 0, output.size());
            if (output.isEmpty()) continue;
            assertArrayEquals(output.get(0).content(),
                    LegacyWorkbenchRecipeEmitter.emitIfPresent(target(id), paths(id)).get(0).content());
            assertTrue(output.get(0).relativePath().endsWith(id.endsWith("-fabric")
                    ? "fabric/v1_21_4/Fabric1201WorkbenchRecipe.java" : "forge/v1_20_1/LegacyForgeWorkbenchRecipe.java"));
        }
    }

    @Test void forgeFamilySharesIdenticalJava() throws Exception {
        var forge = LegacyWorkbenchRecipeEmitter.emitIfPresent(target(TARGETS.get(1)), paths(TARGETS.get(1))).get(0);
        var neo = LegacyWorkbenchRecipeEmitter.emitIfPresent(target(TARGETS.get(2)), paths(TARGETS.get(2))).get(0);
        assertEquals(forge.relativePath(), neo.relativePath());
        assertArrayEquals(forge.content(), neo.content());
    }

    @Test void absentFeatureIsEmptyAndEveryPartialDeclarationFailsClosed() throws Exception {
        for (String id : TARGETS) {
            assertTrue(LegacyWorkbenchRecipeEmitter.emitIfPresent(target(id), Set.of()).isEmpty());
            var required = paths(id).stream().sorted().toList();
            for (int mask = 1; mask < 7; mask++) {
                var selected = new java.util.HashSet<String>();
                for (int bit = 0; bit < 3; bit++) if ((mask & (1 << bit)) != 0) selected.add(required.get(bit));
                assertThrows(BridgeGenerationException.class,
                        () -> LegacyWorkbenchRecipeEmitter.emitIfPresent(target(id), selected));
            }
        }
    }

    @Test void retainsRegistryResolutionAndJsonErrorPolicies() throws Exception {
        for (String id : TARGETS) {
            String source = text(id);
            boolean direct = id.endsWith("-fabric");
            assertTrue(source.contains(direct ? "return serializer;" : "return binding.serializer().get();"));
            assertTrue(source.contains(direct ? "return type;" : "return binding.type().get();"));
            assertEquals(!direct, source.contains("catch (IllegalArgumentException"));
            assertTrue(source.contains("ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, \"result\"))"));
        }
    }

    @Test void guardsJsonAndWireLengthsBeforeAllocating() throws Exception {
        for (String id : TARGETS) {
            String source = text(id);
            String slots = id.endsWith("-fabric") ? "inputSlots" : "binding.inputSlots()";
            int jsonGuard = source.indexOf("if (values.size() != " + slots + ")");
            assertTrue(jsonGuard >= 0 && source.indexOf("new ArrayList<>", jsonGuard) > jsonGuard);
            int read = source.indexOf("int size = buffer.readVarInt();");
            int guard = source.indexOf("if (size != " + slots + ")", read);
            int allocation = source.indexOf("new ArrayList<>(size)", read);
            assertTrue(read >= 0 && guard > read && allocation > guard);
            assertTrue(source.contains("if (count < 1 || count > 64)"));
        }
    }

    @Test void preservesWireFieldOrdering() throws Exception {
        for (String id : TARGETS) {
            String source = text(id);
            assertTrue(source.contains("new Entry(Ingredient.fromNetwork(buffer), buffer.readVarInt())"));
            int size = source.indexOf("buffer.writeVarInt(recipe.ingredients.size());");
            int ingredient = source.indexOf("entry.ingredient().toNetwork(buffer);", size);
            int count = source.indexOf("buffer.writeVarInt(entry.count());", ingredient);
            int result = source.indexOf("buffer.writeItem(recipe.result);", count);
            assertTrue(size >= 0 && ingredient > size && count > ingredient && result > count);
        }
    }

    @Test void retainsCopiesAndPositionalMatchingWithEmptyTrailingSlots() throws Exception {
        for (String id : TARGETS) {
            String source = text(id);
            assertTrue(source.contains("this.ingredients = List.copyOf(ingredients);"));
            assertTrue(source.contains("this.result = result.copy();"));
            assertTrue(source.contains("if (input.getContainerSize() < ingredients.size())"));
            assertTrue(source.contains("!expected.ingredient().test(actual) || actual.getCount() < expected.count()"));
            assertTrue(source.contains("for (int slot = ingredients.size(); slot < input.getContainerSize(); slot++)"));
            assertTrue(source.contains("if (!input.getItem(slot).isEmpty())"));
        }
    }

    @Test void rejectsUnreviewedTargetChanges() {
        TargetSpec base = target(TARGETS.get(0));
        TargetSpec changed = new TargetSpec(base.id(), base.minecraftVersion(), base.loader(), base.javaVersion(),
                "unreviewed", base.platformApiVersion(), base.loaderAbi(), base.minecraftAbi(), base.mappingAbi(),
                base.recipeAbi(), base.networkAbi(), base.menuAbi());
        assertThrows(BridgeGenerationException.class,
                () -> LegacyWorkbenchRecipeEmitter.emitIfPresent(changed, paths(base.id())));
    }

    private static TargetSpec target(String id) { return TargetCatalog.standard().require(id); }
    private static Set<String> paths(String id) {
        boolean fabric = id.endsWith("-fabric");
        String root = "uk/co/enderfall/sdk/runtime/" + (fabric ? "fabric" : "neoforge")
                + "/v1_21_4/" + (fabric ? "Fabric" : "NeoForge");
        return Set.of(root + "WorkbenchRecipe.java", root + "RecipeBinding.java", root + "WorkbenchBinding.java");
    }
    private static String text(String id) throws BridgeGenerationException {
        return new String(LegacyWorkbenchRecipeEmitter.emitIfPresent(target(id), paths(id)).get(0).content(),
                StandardCharsets.UTF_8);
    }
}
