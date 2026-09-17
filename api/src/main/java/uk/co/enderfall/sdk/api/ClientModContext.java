package uk.co.enderfall.sdk.api;

/** Client-only extension point kept separate to protect dedicated-server class loading. */
public interface ClientModContext extends ModContext {
    @uk.co.enderfall.sdk.api.annotation.Experimental("Portable block-entity item rendering")
    default uk.co.enderfall.sdk.api.render.BlockEntityRendererRegistrar blockEntityRenderers() {
        throw new UnsupportedOperationException("Block-entity rendering is unavailable");
    }
}
