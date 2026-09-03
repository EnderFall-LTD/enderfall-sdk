package uk.co.enderfall.sdk.api.registry;

import uk.co.enderfall.sdk.api.ResourceId;

/** Stable reference to content registered by an adapter. */
public interface RegistryRef {
    ResourceId id();
}
