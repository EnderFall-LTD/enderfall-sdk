package uk.co.enderfall.sdk.api;

/** Optional physical-client entrypoint. Dedicated servers never load this type. */
@FunctionalInterface
public interface EnderfallClientMod {
    void initialize(ClientModContext context);
}
