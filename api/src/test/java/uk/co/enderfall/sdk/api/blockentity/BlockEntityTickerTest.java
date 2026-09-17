package uk.co.enderfall.sdk.api.blockentity;

import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.registry.BlockRef;
import static org.junit.jupiter.api.Assertions.*;

class BlockEntityTickerTest {
    private BlockEntitySpec.Builder builder() {
        return BlockEntitySpec.builder(new BlockRef(ResourceId.parse("test:machine")));
    }

    @Test void tickingIsOptInAndRejectsNull() {
        assertTrue(builder().build().serverTicker().isEmpty());
        assertThrows(NullPointerException.class, () -> builder().serverTicker(null));
    }

    @Test void builtDefinitionRetainsItsCallbackWhenBuilderChanges() {
        BlockEntityTicker first = state -> { };
        var builder = builder().serverTicker(first);
        var definition = builder.build();
        builder.serverTicker(state -> { });
        assertSame(first, definition.serverTicker().orElseThrow());
    }

    @Test void sharedCallbackUsesEachInstancesState() {
        var elapsed = new BlockEntityInt("elapsed", 0, 0, 200);
        var definition = builder().field(elapsed)
                .serverTicker(state -> state.set(elapsed, Math.min(200, state.get(elapsed) + 1))).build();
        class State implements BlockEntityState {
            int value;
            public int get(BlockEntityInt field) { return value; }
            public void set(BlockEntityInt field, int value) { this.value = field.validate(value); }
        }
        var first = new State();
        var second = new State();
        for (int i = 0; i < 250; i++) definition.serverTicker().orElseThrow().tick(first);
        definition.serverTicker().orElseThrow().tick(second);
        assertEquals(200, first.value);
        assertEquals(1, second.value);
    }
}
