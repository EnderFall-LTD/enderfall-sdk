package uk.co.enderfall.sdk.preview;

import uk.co.enderfall.sdk.api.block.BlockDirection;
import uk.co.enderfall.sdk.api.block.BlockNeighborContext;
import uk.co.enderfall.sdk.api.block.BlockProperty;
import uk.co.enderfall.sdk.api.block.BlockScheduledTickContext;
import uk.co.enderfall.sdk.api.block.BlockShape;
import uk.co.enderfall.sdk.api.block.BlockStateDefinition;
import uk.co.enderfall.sdk.api.block.BlockStateShapes;
import uk.co.enderfall.sdk.api.block.BlockTransition;
import uk.co.enderfall.sdk.api.block.PortableBlock;
import uk.co.enderfall.sdk.api.block.PortableBlockState;
import uk.co.enderfall.sdk.api.registry.Registration;

/** Portable connected geometry, waterlogging and delayed state cleanup in one ordinary block. */
public final class PreviewConnectingTableBlock implements PortableBlock {
    public static final BlockProperty<Boolean> NORTH = BlockProperty.bool("north");
    public static final BlockProperty<Boolean> EAST = BlockProperty.bool("east");
    public static final BlockProperty<Boolean> SOUTH = BlockProperty.bool("south");
    public static final BlockProperty<Boolean> WEST = BlockProperty.bool("west");
    public static final BlockProperty<Boolean> SETTLING = BlockProperty.bool("settling");
    public static final BlockStateDefinition STATES = BlockStateDefinition.builder()
            .property(NORTH, false).property(EAST, false).property(SOUTH, false)
            .property(WEST, false).property(SETTLING, false).build();
    private static final BlockShape CENTER = BlockShape.union(
            BlockShape.box(5, 12, 5, 11, 16, 11), BlockShape.box(6, 0, 6, 10, 12, 10));
    private static final BlockShape NORTH_ARM = BlockShape.box(5, 12, 0, 11, 16, 5);
    public static final BlockStateShapes SHAPES = BlockStateShapes.create(STATES,
            PreviewConnectingTableBlock::shape);

    private static BlockShape shape(PortableBlockState state) {
        var parts = new java.util.ArrayList<BlockShape>();
        parts.add(CENTER);
        if (state.get(NORTH)) parts.add(NORTH_ARM);
        if (state.get(EAST)) parts.add(NORTH_ARM.rotateY(1));
        if (state.get(SOUTH)) parts.add(NORTH_ARM.rotateY(2));
        if (state.get(WEST)) parts.add(NORTH_ARM.rotateY(3));
        return BlockShape.union(parts.toArray(BlockShape[]::new));
    }

    @Override public void configure(Registration.BlockOptions block) {
        block.states(STATES).stateShapes(SHAPES).waterlogged().scheduledTicks();
    }

    @Override public BlockTransition onNeighborChanged(BlockNeighborContext neighbor) {
        BlockProperty<Boolean> connection = switch (neighbor.direction()) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> null;
        };
        if (connection == null || neighbor.state().get(connection) == neighbor.sameBlock()) {
            return BlockTransition.stable(neighbor.state());
        }
        return BlockTransition.after(neighbor.state().with(connection, neighbor.sameBlock()).with(SETTLING, true), 4);
    }

    @Override public BlockTransition onScheduledTick(BlockScheduledTickContext tick) {
        return BlockTransition.stable(tick.state().with(SETTLING, false));
    }
}
