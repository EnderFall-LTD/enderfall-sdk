package uk.co.enderfall.sdk.runtime.forge.v1_20_1;

import net.minecraftforge.fml.common.Mod;

/** Loader-owned entrypoint for the 1.20.1 EnderFall runtime artifact. */
@Mod("enderfall_sdk")
public final class EnderfallForgeRuntime {
    /** Creates the loader-owned runtime marker entrypoint. */
    public EnderfallForgeRuntime() {
        // Consumer bootstrap classes initialize their own isolated runtime contexts.
    }
}
