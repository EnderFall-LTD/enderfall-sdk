package uk.co.enderfall.sdk.api.blockentity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.registry.BlockRef;

class BlockEntityLootTableTest {
    private static final BlockRef BLOCK = new BlockRef(ResourceId.parse("test:crate"));

    @Test void lootTableStorageRequiresInventoryAndRemainsExplicit() {
        assertFalse(BlockEntitySpec.builder(BLOCK).inventorySlots(9).build().lootTableInventory());
        assertTrue(BlockEntitySpec.builder(BLOCK).inventorySlots(9).lootTableInventory().build().lootTableInventory());
        assertThrows(IllegalArgumentException.class,
                () -> BlockEntitySpec.builder(BLOCK).lootTableInventory().build());
    }
}
