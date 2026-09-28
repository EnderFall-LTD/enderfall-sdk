package uk.co.enderfall.sdk.api.client.resource;

/** Runs on the client thread after client resources have been applied. */
@FunctionalInterface
public interface ClientResourceReloadListener {
    void resourcesReloaded();
}
