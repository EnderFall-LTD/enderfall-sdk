package uk.co.enderfall.sdk.api.block;

import java.util.Objects;
import java.util.function.Consumer;
import uk.co.enderfall.sdk.api.annotation.Experimental;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;
import uk.co.enderfall.sdk.api.registry.BlockRef;
import uk.co.enderfall.sdk.api.registry.Registration;

/** Behavior declaration context. Intentionally excludes material copying and item registration. */
@Experimental("Portable custom block classes")
public final class BlockDefinition {
    private final Registration.BlockOptions properties;
    BlockDefinition(Registration.BlockOptions properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }
    /** Actual registered identity, useful for owned storage; never a placeholder namespace. */
    public BlockRef block() { return properties.block(); }
    public BlockDefinition horizontalFacing() { properties.horizontalFacing(); return this; }
    public BlockDefinition sixWayFacing() { properties.sixWayFacing(); return this; }
    public BlockDefinition states(BlockStateDefinition definition) { properties.states(definition); return this; }
    public BlockDefinition stateShapes(BlockStateShapes shapes) { properties.stateShapes(shapes); return this; }
    public BlockDefinition shape(BlockShape shape) { properties.shape(shape); return this; }
    public BlockDefinition outlineShape(BlockShape shape) { properties.outlineShape(shape); return this; }
    public BlockDefinition collisionShape(BlockShape shape) { properties.collisionShape(shape); return this; }
    /** Storage ownership is assigned to this registration; existing storage semantics create its block item. */
    public BlockDefinition storage(Consumer<BlockEntitySpec.Builder> configure) { properties.storage(configure); return this; }
    public BlockDefinition storage(BlockEntitySpec storage) { properties.storage(storage); return this; }
}
