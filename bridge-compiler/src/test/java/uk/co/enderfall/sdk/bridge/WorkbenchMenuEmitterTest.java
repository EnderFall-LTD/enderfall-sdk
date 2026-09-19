package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

class WorkbenchMenuEmitterTest {
    private static final List<String> TARGETS = List.of("1.21.4-fabric", "1.21.4-neoforge",
            "1.21.1-fabric", "1.21.1-neoforge");

    @Test void selectsOnlyMigratedTargetsDeterministically() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            var output = WorkbenchMenuEmitter.emitIfPresent(target(id), paths(id));
            assertEquals(TARGETS.contains(id) ? 1 : 0, output.size());
            if (output.isEmpty()) continue;
            assertEquals(root(id) + (id.startsWith("1.21.1") ? "1211" : "") + "WorkbenchMenu.java", output.get(0).relativePath());
            assertArrayEquals(output.get(0).content(), WorkbenchMenuEmitter.emitIfPresent(target(id), paths(id)).get(0).content());
        }
    }

    @Test void menuRequiresEveryDependencyButRecipesCanExistWithoutMenu() throws Exception {
        for (String id : TARGETS) {
            var required = paths(id).stream().sorted().toList();
            for (int mask = 0; mask < 31; mask++) {
                var selected = new java.util.HashSet<String>();
                for (int bit = 0; bit < 5; bit++) if ((mask & (1 << bit)) != 0) selected.add(required.get(bit));
                if (selected.contains(root(id) + "WorkbenchMenu.java")) {
                    assertThrows(BridgeGenerationException.class, () -> WorkbenchMenuEmitter.emitIfPresent(target(id), selected));
                } else assertTrue(WorkbenchMenuEmitter.emitIfPresent(target(id), selected).isEmpty());
            }
        }
    }

    @Test void preservesRegistryAccessAndServerAuthoritativeResultUpdates() throws Exception {
        for (String id : TARGETS) {
            String source = text(id);
            String handle = id.endsWith("-fabric") ? "" : ".get()";
            assertTrue(source.contains("super(binding.menuType()" + handle + ", containerId);"));
            boolean modern = id.startsWith("1.21.4");
            assertTrue(source.contains("recipe.getType() == binding.recipes().type()" + handle));
            int reset = source.indexOf("selectedRecipe = null;");
            int guard = source.indexOf(modern ? "if (!(player.level() instanceof ServerLevel level)) return;"
                    : "if (player.level().isClientSide()) return;", reset);
            int lookup = source.indexOf(modern ? "level.recipeAccess().getRecipes()"
                    : "player.level().getRecipeManager().getRecipes()", guard);
            int sync = source.indexOf("broadcastChanges();", lookup);
            assertTrue(reset >= 0 && guard > reset && lookup > guard && sync > lookup);
        }
    }

    @Test void preservesSlotsQuickMoveAndCloseCleanup() throws Exception {
        for (String id : TARGETS) {
            String source = text(id);
            assertTrue(source.contains("addSlot(new Slot(inputs, slot, inputStart + slot * 18, 35));"));
            assertTrue(source.contains("mayPlace(ItemStack stack) { return false; }"));
            assertTrue(source.contains("moveItemStackTo(moving, machineSlots, slots.size(), true)"));
            assertTrue(source.contains("moveItemStackTo(moving, 0, binding.recipes().inputSlots(), false)"));
            assertTrue(source.contains("if (!player.level().isClientSide()) clearContainer(player, inputs);"));
            assertTrue(source.contains("inputs.getItems().stream().map(ItemStack::copy).toList()"));
        }
    }

    @Test void discoversAndSynchronizesSelectableRecipePagesWithoutConsumerPackets() throws Exception {
        for (String id : TARGETS) {
            String source = text(id);
            assertTrue(source.contains("new SimpleContainer(binding.definition().spec().recipeBrowserEntries())"));
            assertTrue(source.contains("matchingRecipes.get(index).value().result().copy()"));
            assertTrue(source.contains("DataSlot selectedRecipeIndex = DataSlot.standalone()"));
            assertTrue(source.contains("public boolean clickMenuButton(Player player, int id)"));
            assertTrue(source.contains("String previousId = selectedRecipe == null"));
            assertTrue(source.contains("requiredCounts[slot].set(ingredients.get(slot).count())"));
        }
    }

    @Test void revalidatesBeforeConsumingAndNotifiesAfterConsumption() throws Exception {
        for (String id : TARGETS) {
            String source = text(id);
            int guard = source.indexOf("if (holder == null || !holder.value().matches(input, player.level()))");
            int consume = source.indexOf("inputs.removeItem(slot, ingredients.get(slot).count());");
            int notify = source.indexOf("binding.definition().craftListener().accept(");
            assertTrue(guard >= 0 && consume > guard && notify > consume);
            assertTrue(source.contains(id.startsWith("1.21.4")
                    ? "var recipeId = holder.id().location();" : "var recipeId = holder.id();"));
        }
    }

    @Test void rejectsUnreviewedTargetChanges() {
        TargetSpec base = target(TARGETS.get(0));
        TargetSpec changed = new TargetSpec(base.id(), base.minecraftVersion(), base.loader(), base.javaVersion(),
                "unreviewed", base.platformApiVersion(), base.loaderAbi(), base.minecraftAbi(), base.mappingAbi(),
                base.recipeAbi(), base.networkAbi(), base.menuAbi());
        assertThrows(BridgeGenerationException.class, () -> WorkbenchMenuEmitter.emitIfPresent(changed, paths(base.id())));
    }

    private static TargetSpec target(String id) { return TargetCatalog.standard().require(id); }
    private static String root(String id) {
        boolean fabric = id.endsWith("-fabric");
        return "uk/co/enderfall/sdk/runtime/" + (fabric ? "fabric" : "neoforge") + "/v1_21_4/" + (fabric ? "Fabric" : "NeoForge");
    }
    private static Set<String> paths(String id) {
        String root = root(id);
        return Set.of(root + "WorkbenchMenu.java", root + "WorkbenchBinding.java", root + "RecipeBinding.java",
                root + "WorkbenchRecipe.java", root + "WorkbenchInput.java");
    }
    private static String text(String id) throws BridgeGenerationException {
        return new String(WorkbenchMenuEmitter.emitIfPresent(target(id), paths(id)).get(0).content(), StandardCharsets.UTF_8);
    }
}
