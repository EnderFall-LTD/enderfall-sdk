package uk.co.enderfall.sdk.preview;

import uk.co.enderfall.sdk.api.block.BlockProperty;
import uk.co.enderfall.sdk.api.block.BlockStateDefinition;
import uk.co.enderfall.sdk.api.block.PortableBlock;
import uk.co.enderfall.sdk.api.registry.Registration;

/** A plain portable block whose open state is owned by the container viewer lifecycle. */
public final class PreviewStorageCabinetBlock implements PortableBlock {
    public static final BlockProperty<Boolean> OPEN = BlockProperty.bool("open");
    public static final BlockStateDefinition STATES = BlockStateDefinition.builder()
            .property(OPEN, false)
            .build();

    @Override
    public void configure(Registration.BlockOptions properties) {
        properties.states(STATES);
    }
}
