package uk.co.enderfall.sdk.runtime.fabric.v1_21_4;

import net.fabricmc.api.ModInitializer;

/** Fabric-owned entrypoint for the 1.21.4 EnderFall runtime artifact. */
public final class EnderfallFabricRuntime implements ModInitializer {
    /** Creates the loader-owned runtime marker entrypoint. */
    public EnderfallFabricRuntime() {
    }

    @Override
    public void onInitialize() {
        // Consumer bootstrap classes initialize their own isolated runtime contexts.
    }
}
