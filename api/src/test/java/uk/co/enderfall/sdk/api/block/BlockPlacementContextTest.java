package uk.co.enderfall.sdk.api.block;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BlockPlacementContextTest {
    @Test void directionsExposeStablePortableSemantics() {
        assertEquals(BlockDirection.SOUTH, BlockDirection.NORTH.opposite());
        assertEquals(BlockDirection.DOWN, BlockDirection.UP.opposite());
        assertTrue(BlockDirection.WEST.horizontal());
        assertFalse(BlockDirection.UP.horizontal());
    }

    @Test void playerFacingMustRemainHorizontal() {
        var state = BlockStateDefinition.builder().build().defaultState();
        assertThrows(IllegalArgumentException.class, () -> new BlockPlacementContext(
                state, BlockDirection.UP, BlockDirection.DOWN, BlockDirection.NORTH));
        assertSame(state, new BlockPlacementContext(state, BlockDirection.UP,
                BlockDirection.WEST, BlockDirection.DOWN).state());
    }
}
