package uk.co.enderfall.sdk.preview;

import uk.co.enderfall.sdk.api.block.CustomBlock;
import uk.co.enderfall.sdk.api.block.BlockDefinition;
import uk.co.enderfall.sdk.api.block.BlockShape;

/** A reusable block definition, with no Minecraft or loader superclass. */
public final class PreviewWorkbenchBlock extends CustomBlock {
    public static final BlockShape SHAPE = BlockShape.union(
            BlockShape.box(0, 12, 0, 16, 16, 16),
            BlockShape.box(1, 0, 1, 3, 12, 3),
            BlockShape.box(13, 0, 1, 15, 12, 3),
            BlockShape.box(1, 0, 13, 3, 12, 15),
            BlockShape.box(13, 0, 13, 15, 12, 15),
            BlockShape.box(3, 4, 13, 13, 7, 15));
    @Override
    protected void define(BlockDefinition block) {
        block.horizontalFacing().shape(SHAPE).storage(PreviewBlocks.WORKBENCH_STORAGE);
    }
}
