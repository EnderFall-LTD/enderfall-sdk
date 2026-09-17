package uk.co.enderfall.sdk.preview;

import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.block.BlockProperty;
import uk.co.enderfall.sdk.api.block.BlockDirection;
import uk.co.enderfall.sdk.api.block.BlockPlacementContext;
import uk.co.enderfall.sdk.api.block.BlockShape;
import uk.co.enderfall.sdk.api.block.BlockStateDefinition;
import uk.co.enderfall.sdk.api.block.BlockStateShapes;
import uk.co.enderfall.sdk.api.block.PortableBlock;
import uk.co.enderfall.sdk.api.event.InteractionEvent;
import uk.co.enderfall.sdk.api.registry.Registration;

/** Ordinary block proving six-way facing plus real server-world state mutation. */
public final class PreviewOrientationBlock implements PortableBlock {
    public static final BlockProperty<Boolean> COMPACT = BlockProperty.bool("compact");
    public static final BlockStateDefinition STATES = BlockStateDefinition.builder()
            .property(COMPACT, false).build();
    public static final BlockStateShapes SHAPES = BlockStateShapes.create(STATES, state -> state.get(COMPACT)
            ? BlockShape.box(0, 0, 0, 16, 4, 16) : PreviewWorkbenchBlock.SHAPE);

    @Override public void configure(Registration.BlockOptions properties) {
        properties.sixWayFacing().states(STATES).stateShapes(SHAPES);
    }

    /** Ceiling placement starts compact, proving portable placement-state selection. */
    @Override public uk.co.enderfall.sdk.api.block.PortableBlockState onPlace(BlockPlacementContext placement) {
        return placement.state().with(COMPACT, placement.clickedFace() == BlockDirection.DOWN);
    }

    @Override public void onUse(ModContext context, InteractionEvent event) {
        event.blockLocation().ifPresent(location -> {
            if (context.blockStates().update(PreviewBlocks.ORIENTATION_TEST, location,
                    state -> state.with(COMPACT, !state.get(COMPACT)))) event.handle();
        });
    }
}
