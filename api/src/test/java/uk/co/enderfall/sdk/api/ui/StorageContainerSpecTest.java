package uk.co.enderfall.sdk.api.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.block.BlockProperty;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;
import uk.co.enderfall.sdk.api.registry.BlockRef;

class StorageContainerSpecTest {
    private static BlockEntitySpec storage(int slots) {
        return BlockEntitySpec.builder(new BlockRef(ResourceId.of("test", "cabinet")))
                .inventorySlots(slots).build();
    }

    @Test void supportsEveryVanillaNineWideStorageSize() {
        for (int rows = 1; rows <= 6; rows++) {
            var spec = StorageContainerSpec.builder("Cabinet", storage(rows * 9)).build();
            assertEquals(rows, spec.rows());
            assertEquals(ContainerSoundProfile.BARREL, spec.sounds());
        }
    }

    @Test void rejectsLayoutsThatCannotUseThePortableVanillaScreen() {
        for (int slots : new int[] {0, 1, 8, 10, 53, 55, 63}) {
            assertThrows(IllegalArgumentException.class,
                    () -> StorageContainerSpec.builder("Cabinet", storage(slots)).build());
        }
    }

    @Test void declaresOptionalOpenStateAndCustomSounds() {
        var open = BlockProperty.bool("open");
        var sounds = ContainerSoundProfile.of(ResourceId.of("test", "cabinet.open"),
                ResourceId.of("test", "cabinet.close"), 0.75F, 1.2F);
        var spec = StorageContainerSpec.builder("Cabinet", storage(27))
                .openState(open).sounds(sounds).build();
        assertEquals(open, spec.openProperty().orElseThrow());
        assertEquals(sounds, spec.sounds());
        assertFalse(StorageContainerSpec.builder("Silent", storage(9)).silent().build().sounds().enabled());
        assertThrows(IllegalArgumentException.class, () -> new ContainerSoundProfile(
                ResourceId.of("test", "open"), null, 1, 1));
    }

    @Test void supportsAuthoredInputsOutputsPlayerInventoryAndQuickMoveRules() {
        var spec = StorageContainerSpec.builder("Washer", storage(3))
                .size(176, 166)
                .slots(
                        InventorySlotSpec.accepting(0, 44, 28, "inputs",
                                java.util.List.of(ResourceId.of("minecraft", "amethyst_shard"))).maximumCount(16),
                        InventorySlotSpec.input(1, 66, 28, "inputs"),
                        InventorySlotSpec.output(2, 116, 28, "outputs"))
                .playerInventory(7, 83)
                .quickMove("inputs", InventoryQuickMoveRule.PLAYER_MAIN, InventoryQuickMoveRule.PLAYER_HOTBAR)
                .quickMove("outputs", InventoryQuickMoveRule.PLAYER_MAIN, InventoryQuickMoveRule.PLAYER_HOTBAR)
                .quickMove(InventoryQuickMoveRule.PLAYER_MAIN, "inputs")
                .quickMove(InventoryQuickMoveRule.PLAYER_HOTBAR, "inputs")
                .build();

        assertTrue(spec.customLayout());
        assertEquals(3, spec.slots().size());
        assertTrue(spec.playerInventory());
        assertEquals(4, spec.quickMoveRules().size());
        assertEquals(InventorySlotRole.OUTPUT, spec.slots().get(2).role());
        assertThrows(IllegalStateException.class, spec::rows);
    }

    @Test void validatesAuthoredInventoryOwnershipAndRoutes() {
        assertThrows(IllegalArgumentException.class, () -> StorageContainerSpec.builder("Missing", storage(2))
                .slot(InventorySlotSpec.input(0, 10, 10, "inputs")).build());
        assertThrows(IllegalArgumentException.class, () -> StorageContainerSpec.builder("Duplicate", storage(2))
                .slots(InventorySlotSpec.input(0, 10, 10, "inputs"),
                        InventorySlotSpec.output(0, 40, 10, "outputs")).build());
        assertThrows(IllegalArgumentException.class, () -> StorageContainerSpec.builder("Unknown route", storage(1))
                .slot(InventorySlotSpec.input(0, 10, 10, "inputs"))
                .quickMove("inputs", "missing").build());
        assertThrows(IllegalArgumentException.class, () -> InventorySlotSpec.output(0, 0, 0, "output")
                .maximumCount(0));
    }
}
