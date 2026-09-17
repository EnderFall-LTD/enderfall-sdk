package uk.co.enderfall.sdk.api.block;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;

class BlockNeighborContextTest {
    @Test void retainsPortableNeighbourIdentityWithoutNativeObjects() {
        var open = BlockProperty.bool("open");
        var definition = BlockStateDefinition.builder().property(open, false).build();
        var state = definition.defaultState();
        var context = new BlockNeighborContext(state, BlockDirection.WEST,
                ResourceId.of("test", "table"), true, Optional.of(state.with(open, true)));
        assertTrue(context.sameBlock());
        assertTrue(context.neighborState().orElseThrow().get(open));
        assertEquals(ResourceId.of("test", "table"), context.neighborBlock());
    }
}
