package uk.co.enderfall.sdk.api.client.resource;

import java.util.concurrent.CompletionStage;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.event.Subscription;

/** Portable client resource reload registration and refresh requests. */
public interface ClientResourceManager {
    /**
     * Registers a uniquely identified listener during client initialization.
     * The listener also receives Minecraft's initial client-resource load.
     */
    Subscription onReload(ResourceId id, ClientResourceReloadListener listener);

    /** Requests a complete client resource reload and completes after resources are applied. */
    CompletionStage<Void> reload();
}
