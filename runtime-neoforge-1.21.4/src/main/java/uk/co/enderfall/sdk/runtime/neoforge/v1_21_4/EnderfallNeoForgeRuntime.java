package uk.co.enderfall.sdk.runtime.neoforge.v1_21_4;

import net.neoforged.fml.common.Mod;

/** NeoForge-owned entrypoint for the 1.21.4 EnderFall runtime artifact. */
@Mod("enderfall_sdk")
public final class EnderfallNeoForgeRuntime {
    /** Creates the loader-owned runtime marker entrypoint. */
    public EnderfallNeoForgeRuntime() {
        // Consumer bootstrap classes initialize their own isolated runtime contexts.
    }
}
