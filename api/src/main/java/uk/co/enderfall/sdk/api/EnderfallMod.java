package uk.co.enderfall.sdk.api;

/** Common entrypoint implemented once by a portable EnderFall SDK mod. */
@FunctionalInterface
public interface EnderfallMod {
    void initialize(ModContext context);
}
