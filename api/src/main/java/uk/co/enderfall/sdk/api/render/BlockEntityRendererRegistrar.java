package uk.co.enderfall.sdk.api.render;

import uk.co.enderfall.sdk.api.annotation.Experimental;
import uk.co.enderfall.sdk.api.registry.BlockRef;

/** Client initialization only. Register after the consumer's persistent block, once per block.
 * Registration must fail when the target has no implementation; it must never silently do nothing.
 */
@Experimental("Portable block-entity item rendering; target and inventory support are incomplete")
public interface BlockEntityRendererRegistrar {
    void register(BlockRef block, BlockEntityRenderSpec spec);
}
