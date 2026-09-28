package uk.co.enderfall.sdk.api;

/** Client-only extension point kept separate to protect dedicated-server class loading. */
public interface ClientModContext extends ModContext {
    @uk.co.enderfall.sdk.api.annotation.Experimental("Portable client commands")
    default uk.co.enderfall.sdk.api.client.command.ClientCommandManager clientCommands() {
        throw new UnsupportedOperationException("Portable client commands are unavailable");
    }

    @uk.co.enderfall.sdk.api.annotation.Experimental("Portable block-entity item rendering")
    default uk.co.enderfall.sdk.api.render.BlockEntityRendererRegistrar blockEntityRenderers() {
        throw new UnsupportedOperationException("Block-entity rendering is unavailable");
    }

    @uk.co.enderfall.sdk.api.annotation.Experimental("General portable client screens")
    default uk.co.enderfall.sdk.api.client.ui.ClientScreenManager screens() {
        throw new UnsupportedOperationException("Portable client screens are unavailable");
    }

    @uk.co.enderfall.sdk.api.annotation.Experimental("Portable client resource reloads")
    default uk.co.enderfall.sdk.api.client.resource.ClientResourceManager resources() {
        throw new UnsupportedOperationException("Portable client resources are unavailable");
    }
}
