package uk.co.enderfall.sdk.api.block;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.blockentity.BlockLocation;

import static org.junit.jupiter.api.Assertions.*;

class BlockTransitionTest {
    @Test void stableAndScheduledTransitionsAreImmutableAndBounded() {
        var state = BlockStateDefinition.builder().build().defaultState();
        assertEquals(OptionalInt.empty(), BlockTransition.stable(state).scheduleAfterTicks());
        assertEquals(4, BlockTransition.after(state, 4).scheduleAfterTicks().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> BlockTransition.after(state, 0));
        assertThrows(NullPointerException.class, () -> BlockTransition.stable(null));
    }

    @Test void defaultPortableBlockBridgesExistingCallbacks() {
        var open = BlockProperty.bool("open");
        var definition = BlockStateDefinition.builder().property(open, false).build();
        PortableBlock block = new PortableBlock() {
            @Override public void configure(uk.co.enderfall.sdk.api.registry.Registration.BlockOptions properties) { }
            @Override public PortableBlockState onPlace(BlockPlacementContext context) {
                return context.state().with(open, true);
            }
            @Override public PortableBlockState onNeighborUpdate(BlockNeighborContext context) {
                return context.state().with(open, true);
            }
        };
        var state = definition.defaultState();
        var placement = new BlockPlacementContext(state, BlockDirection.UP, BlockDirection.NORTH, BlockDirection.DOWN);
        assertTrue(block.onPlaced(placement).state().get(open));
        var neighbor = new BlockNeighborContext(state, BlockDirection.EAST,
                ResourceId.of("minecraft", "stone"), false, java.util.Optional.empty());
        assertTrue(block.onNeighborChanged(neighbor).state().get(open));
        var tick = new BlockScheduledTickContext(state,
                new BlockLocation(ResourceId.of("minecraft", "overworld"), 1, 2, 3), 42);
        assertSame(state, block.onScheduledTick(tick).state());
    }
}
