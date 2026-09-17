package uk.co.enderfall.sdk.api.block;

import uk.co.enderfall.sdk.api.annotation.Experimental;
import uk.co.enderfall.sdk.api.registry.Registration;

/**
 * Base class for reusable SDK block behavior. Registry identity and material properties
 * remain at the registration site. Instances describe a block type, not a placed block.
 */
@Experimental("Portable custom block classes")
public abstract class CustomBlock implements PortableBlock {
    protected CustomBlock() { }

    /** Declare facing, geometry and persistent storage here; called once during preflight. */
    protected abstract void define(BlockDefinition block);

    /** Compatibility bridge for the existing registration pipeline. */
    @Override
    public final void configure(Registration.BlockOptions properties) {
        define(new BlockDefinition(properties));
    }
}
