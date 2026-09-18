package uk.co.enderfall.sdk.api.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.block.BlockDirection;
import uk.co.enderfall.sdk.api.registry.BlockRef;

class InventoryAccessSpecTest {
    private static final BlockRef BLOCK = new BlockRef(ResourceId.of("example", "cabinet"));

    @Test void supportsDifferentRulesForTopSidesAndBottom() {
        var access = storage(27, rules -> rules
                .face(BlockDirection.UP, InventoryAccessMode.INSERT)
                .horizontalFaces(InventoryAccessMode.BOTH)
                .face(BlockDirection.DOWN, InventoryAccessMode.EXTRACT));

        assertEquals(27, access.slots(BlockDirection.NORTH).length);
        assertTrue(access.canInsert(BlockDirection.UP, 26));
        assertFalse(access.canExtract(BlockDirection.UP, 26));
        assertTrue(access.canInsert(BlockDirection.WEST, 10));
        assertTrue(access.canExtract(BlockDirection.WEST, 10));
        assertFalse(access.canInsert(BlockDirection.DOWN, 0));
        assertTrue(access.canExtract(BlockDirection.DOWN, 0));
    }

    @Test void supportsRestrictedSlotListsWithoutLeakingMutableArrays() {
        var access = storage(6, rules -> rules
                .insert(BlockDirection.UP, 2, 0, 1)
                .extract(BlockDirection.UP, 5));

        assertArrayEquals(new int[] {0, 1, 2, 5}, access.slots(BlockDirection.UP));
        int[] leaked = access.slots(BlockDirection.UP);
        leaked[0] = 4;
        assertArrayEquals(new int[] {0, 1, 2, 5}, access.slots(BlockDirection.UP));
        assertFalse(access.canInsert(BlockDirection.UP, 3));
        assertFalse(access.canInsert(BlockDirection.UP, 5));
        assertTrue(access.canExtract(BlockDirection.UP, 5));
        assertTrue(access.canExtractFromAnyFace(5));
        assertFalse(access.canExtractFromAnyFace(4));
    }

    @Test void rejectsInvalidAndDuplicateSlotsAtDefinitionTime() {
        assertThrows(IllegalArgumentException.class,
                () -> storage(3, rules -> rules.face(BlockDirection.UP, InventoryAccessMode.INSERT, 3)));
        assertThrows(IllegalArgumentException.class,
                () -> storage(3, rules -> rules.face(BlockDirection.UP, InventoryAccessMode.INSERT, 1, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> storage(3, rules -> rules.face(BlockDirection.UP, InventoryAccessMode.INSERT, new int[0])));
    }

    @Test void defaultsToNoAutomationUnlessAContainerAddsItsConventionalDefault() {
        var spec = BlockEntitySpec.builder(BLOCK).inventorySlots(9).build();
        assertTrue(spec.inventoryAccess().isEmpty());

        var all = InventoryAccessSpec.allFaces(9);
        for (var face : BlockDirection.values()) {
            assertEquals(9, all.slots(face).length);
            assertTrue(all.canInsert(face, 8));
            assertTrue(all.canExtract(face, 8));
        }
    }

    private static InventoryAccessSpec storage(int slots, Consumer<InventoryAccessSpec.Builder> configure) {
        return BlockEntitySpec.builder(BLOCK).inventorySlots(slots).inventoryAccess(configure).build()
                .inventoryAccess().orElseThrow();
    }
}
