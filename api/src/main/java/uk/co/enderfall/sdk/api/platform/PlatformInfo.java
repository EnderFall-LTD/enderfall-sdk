package uk.co.enderfall.sdk.api.platform;

import java.util.Optional;

/** Loader, game-version, and mod-presence information for the running artifact. */
public interface PlatformInfo {
    Loader loader();

    MinecraftVersion minecraftVersion();

    Environment environment();

    boolean isModLoaded(String modId);

    Optional<String> modVersion(String modId);

    default String targetId() {
        return minecraftVersion() + "-" + loader().id();
    }
}
