package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;

class ContainerWorkbenchMenuEmitterTest {
    private static final List<String> TARGETS = List.of("1.20.1-fabric", "1.20.1-forge", "1.20.1-neoforge", "26.2-fabric", "26.2-neoforge");

    @Test void selectsOnlyRemainingMenuAbisDeterministically() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            var output = emit(id, paths(id));
            assertEquals(TARGETS.contains(id) ? 1 : 0, output.size());
            if (output.isEmpty()) continue;
            assertArrayEquals(output.get(0).content(), emit(id, paths(id)).get(0).content());
        }
        assertArrayEquals(emit("1.20.1-forge", paths("1.20.1-forge")).get(0).content(),
                emit("1.20.1-neoforge", paths("1.20.1-neoforge")).get(0).content());
    }

    @Test void menuRequiresDependenciesButAbsenceDoesNotGenerateStrayClasses() throws Exception {
        for (String id : TARGETS) {
            assertTrue(emit(id, Set.of()).isEmpty());
            for (String missing : paths(id)) {
                if (missing.endsWith("WorkbenchMenu.java")) continue;
                var partial = new java.util.HashSet<>(paths(id)); partial.remove(missing);
                assertThrows(BridgeGenerationException.class, () -> emit(id, partial));
            }
        }
    }

    @Test void retainsDirectVersusHolderRecipesAndCorrectAssembly() throws Exception {
        for (String id : TARGETS) {
            String source = text(id);
            boolean legacy = id.startsWith("1.20.1");
            assertEquals(!legacy, source.contains("RecipeHolder<"));
            assertEquals(!legacy, source.contains("recipeInput()"));
            assertTrue(source.contains(legacy ? ".assemble(inputs, player.level().registryAccess())" : ".value().assemble(input)"));
            assertTrue(source.contains(legacy ? "recipe.getId()" : "holder.id().identifier()"));
            assertTrue(source.contains(legacy ? "player.level().isClientSide()" : "player instanceof ServerPlayer serverPlayer"));
            assertTrue(source.contains("broadcastChanges();"));
        }
    }

    @Test void retainsSlotExclusionsCleanupAndCraftRevalidation() throws Exception {
        for (String id : TARGETS) {
            String source = text(id);
            assertTrue(source.contains("mayPlace(ItemStack stack) { return false; }"));
            assertTrue(source.contains("moveItemStackTo(moving, 0, binding.recipes().inputSlots(), false)"));
            assertTrue(source.contains("moveItemStackTo(moving, machineSlots, slots.size(), true)"));
            assertTrue(source.contains("clearContainer(player, inputs);"));
            int craft = source.indexOf("private void completeCraft(");
            int validate = source.indexOf(".matches(", craft);
            int consume = source.indexOf("inputs.removeItem(", craft);
            int notify = source.indexOf("craftListener().accept(", craft);
            assertTrue(craft >= 0 && validate > craft && consume > validate && notify > consume);
        }
    }

    @Test void discoversAndSynchronizesSelectableRecipePagesWithoutConsumerPackets() throws Exception {
        for (String id : TARGETS) {
            String source = text(id);
            assertTrue(source.contains("new SimpleContainer(binding.definition().spec().recipeBrowserEntries())"));
            assertTrue(source.contains("DataSlot selectedRecipeIndex = DataSlot.standalone()"));
            assertTrue(source.contains("public boolean clickMenuButton(Player player, int id)"));
            assertTrue(source.contains("String previousId = selectedRecipe == null"));
            assertTrue(source.contains("requiredCounts[slot].set(ingredients.get(slot).count())"));
            assertTrue(source.contains(id.startsWith("1.20.1")
                    ? "getAllRecipesFor(binding.recipes().type()" : "recipeAccess().getRecipes()"));
        }
    }

    @Test void preservesLegacyRegistryAndConstructorPolicies() throws Exception {
        assertTrue(text("1.20.1-fabric").contains("super(type, containerId);"));
        assertTrue(text("1.20.1-forge").contains("super(binding.menuType().get(), containerId);"));
        assertTrue(text("1.20.1-forge").contains("ForgeRegistries.ITEMS.getKey(completed.getItem())"));
        assertTrue(text("26.2-neoforge").contains("BuiltInRegistries.ITEM.getKey(completed.getItem())"));
    }

    private static List<RuntimeSource> emit(String id, Set<String> paths) throws BridgeGenerationException {
        return ContainerWorkbenchMenuEmitter.emitIfPresent(TargetCatalog.standard().require(id), paths);
    }
    private static Set<String> paths(String id) {
        boolean fabric = id.endsWith("-fabric");
        String root = "uk/co/enderfall/sdk/runtime/" + (fabric ? "fabric" : "neoforge") + "/v1_21_4/" + (fabric ? "Fabric" : "NeoForge");
        var paths = new java.util.HashSet<>(Set.of(root + "WorkbenchMenu.java", root + "WorkbenchBinding.java", root + "RecipeBinding.java", root + "WorkbenchRecipe.java"));
        if (!id.startsWith("1.20.1")) paths.add(root + "WorkbenchInput.java");
        return paths;
    }
    private static String text(String id) throws BridgeGenerationException {
        return new String(emit(id, paths(id)).get(0).content(), StandardCharsets.UTF_8);
    }
}
