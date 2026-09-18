package uk.co.enderfall.sdk.preview;

import uk.co.enderfall.sdk.api.block.BlockShape;
import uk.co.enderfall.sdk.api.block.PortableBlock;
import uk.co.enderfall.sdk.api.registry.Registration;

/** Log/pillar-style placement proof: opposite clicked faces share X, Y or Z. */
public final class PreviewAxisBlock implements PortableBlock {
    /** Authored vertically on Y; the SDK rotates this with the native axis state. */
    public static final BlockShape SHAPE = BlockShape.union(
            BlockShape.box(5, 0, 5, 11, 16, 11),
            BlockShape.box(3, 6, 3, 13, 10, 13));

    @Override public void configure(Registration.BlockOptions properties) {
        properties.axisFacing().shape(SHAPE);
    }
}
