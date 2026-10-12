package uk.co.enderfall.sdk.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;
import uk.co.enderfall.sdk.api.config.ConfigSpec;
import uk.co.enderfall.sdk.api.recipe.WorkbenchRecipeTypeRef;
import uk.co.enderfall.sdk.api.registry.BlockRef;
import uk.co.enderfall.sdk.api.ui.MenuButton;
import uk.co.enderfall.sdk.api.ui.MenuSelectionList;
import uk.co.enderfall.sdk.api.ui.MenuSpec;
import uk.co.enderfall.sdk.api.ui.WorkbenchSpec;

/**
 * A builder that records state before it validates leaves a caller that catches the rejection with
 * a corrupted builder. These tests assert that a rejected declaration changes nothing, so a
 * corrected retry behaves exactly as if the failed call had never happened.
 */
class BuilderExceptionSafetyTest {
    @Test
    void aRejectedConfigKeyLeavesThePathAvailableForAValidRetry() {
        ConfigSpec.Builder builder = ConfigSpec.builder();

        assertThrows(IllegalArgumentException.class,
                () -> builder.integer("graphics.fov", 200, 0, 100, "Out of range default."));

        // The rejected attempt must not have consumed the path.
        assertEquals(70, builder.integer("graphics.fov", 70, 0, 100, "FOV.").defaultValue());
        assertEquals(1, builder.build().keys().size());
    }

    @Test
    void aRejectedConfigKeyStillReportsDuplicatesCorrectly() {
        ConfigSpec.Builder builder = ConfigSpec.builder();
        builder.integer("graphics.fov", 70, 0, 100, "FOV.");
        assertThrows(IllegalArgumentException.class,
                () -> builder.integer("graphics.fov", 90, 0, 100, "FOV."));
        assertEquals(1, builder.build().keys().size());
    }

    @Test
    void aRejectedPersistentBindingLeavesTheMachineDefinitionIntact() {
        // One recipe input slot, so a timed machine binds inputs plus one stored output slot.
        WorkbenchRecipeTypeRef type = new WorkbenchRecipeTypeRef(ResourceId.of("test_mod", "furnace"), 1);
        BlockEntitySpec matching = BlockEntitySpec.builder(new BlockRef(ResourceId.of("test_mod", "furnace")))
                .inventorySlots(2).build();
        BlockEntitySpec wrong = BlockEntitySpec.builder(new BlockRef(ResourceId.of("test_mod", "other")))
                .inventorySlots(4).build();

        WorkbenchSpec.Builder builder = WorkbenchSpec.builder("Furnace", type)
                .persistentMachine(matching, List.of(), List.of());
        assertTrue(builder.build().machineRecipes());

        assertThrows(IllegalArgumentException.class, () -> builder.persistent(wrong));

        // The failed call must not have wiped the machine tanks.
        WorkbenchSpec spec = builder.build();
        assertTrue(spec.machineRecipes());
        assertEquals(List.of(), spec.fluidInputTanks());
    }

    @Test
    void aRejectedTimedBindingLeavesTheBuilderUnchanged() {
        WorkbenchRecipeTypeRef type = new WorkbenchRecipeTypeRef(ResourceId.of("test_mod", "furnace"), 1);
        BlockEntitySpec wrong = BlockEntitySpec.builder(new BlockRef(ResourceId.of("test_mod", "other")))
                .inventorySlots(4).build();

        WorkbenchSpec.Builder builder = WorkbenchSpec.builder("Furnace", type)
                .persistentMachine(BlockEntitySpec.builder(new BlockRef(ResourceId.of("test_mod", "furnace")))
                        .inventorySlots(2).build(), List.of(), List.of());

        assertThrows(IllegalArgumentException.class, () -> builder.persistentTimed(wrong, 100));
        assertThrows(NullPointerException.class, () -> builder.persistentTimed(null, 100));
        assertThrows(IllegalArgumentException.class,
                () -> builder.persistentTimed(BlockEntitySpec.builder(
                        new BlockRef(ResourceId.of("test_mod", "furnace"))).inventorySlots(2).build(), 0));

        assertTrue(builder.build().machineRecipes());
    }

    @Test
    void aSelectionListWithACollidingButtonRegistersNothing() {
        MenuSelectionList list = MenuSelectionList.of("recipes", 8, 20, 120, 3);
        MenuSpec.Builder builder = MenuSpec.builder("Recipes").size(160, 120)
                .button(new MenuButton("recipes.select.0", "X", 8, 20, 120, 20));

        assertThrows(IllegalArgumentException.class, () -> builder.selectionList(list));

        // Neither the list key nor its paging buttons may have been registered by the failed call,
        // so the corrected menu can be declared afterwards.
        MenuSpec spec = MenuSpec.builder("Recipes").size(160, 120).selectionList(list).build();
        assertEquals(1, spec.selectionLists().size());
        assertFalse(spec.buttons().stream()
                .anyMatch(button -> button.text().equals("X")));
    }

    @Test
    void aRejectedSelectionListCanBeRetriedWithTheSameKey() {
        MenuSelectionList list = MenuSelectionList.of("recipes", 8, 20, 120, 3);
        MenuSpec.Builder builder = MenuSpec.builder("Recipes").size(160, 120)
                .button(new MenuButton("recipes.select.0", "X", 8, 20, 120, 20));

        assertThrows(IllegalArgumentException.class, () -> builder.selectionList(list));
        // The second attempt must fail for the original reason, not a stale key collision.
        IllegalArgumentException retry = assertThrows(IllegalArgumentException.class,
                () -> builder.selectionList(list));
        assertTrue(retry.getMessage().contains("recipes.select.0"), retry.getMessage());
    }
}