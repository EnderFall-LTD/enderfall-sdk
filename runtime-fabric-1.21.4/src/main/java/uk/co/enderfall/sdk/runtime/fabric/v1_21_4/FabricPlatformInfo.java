package uk.co.enderfall.sdk.runtime.fabric.v1_21_4;

import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import uk.co.enderfall.sdk.api.platform.Environment;
import uk.co.enderfall.sdk.api.platform.Loader;
import uk.co.enderfall.sdk.api.platform.MinecraftVersion;
import uk.co.enderfall.sdk.api.platform.PlatformInfo;

final class FabricPlatformInfo implements PlatformInfo {
    private final FabricLoader loader = FabricLoader.getInstance();

    @Override
    public Loader loader() {
        return Loader.FABRIC;
    }

    @Override
    public MinecraftVersion minecraftVersion() {
        String version = loader.getModContainer("minecraft")
                .orElseThrow(() -> new IllegalStateException("Minecraft mod container is unavailable"))
                .getMetadata().getVersion().getFriendlyString();
        return new MinecraftVersion(version);
    }

    @Override
    public Environment environment() {
        return loader.getEnvironmentType() == EnvType.CLIENT
                ? Environment.CLIENT : Environment.DEDICATED_SERVER;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return loader.isModLoaded(modId);
    }

    @Override
    public Optional<String> modVersion(String modId) {
        return loader.getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString());
    }
}
