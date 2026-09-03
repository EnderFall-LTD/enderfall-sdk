package uk.co.enderfall.sdk.runtime.neoforge.v1_21_4;

import java.util.Optional;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import uk.co.enderfall.sdk.api.platform.Environment;
import uk.co.enderfall.sdk.api.platform.Loader;
import uk.co.enderfall.sdk.api.platform.MinecraftVersion;
import uk.co.enderfall.sdk.api.platform.PlatformInfo;

final class NeoForgePlatformInfo implements PlatformInfo {
    @Override
    public Loader loader() {
        return Loader.NEOFORGE;
    }

    @Override
    public MinecraftVersion minecraftVersion() {
        String version = ModList.get().getModContainerById("minecraft")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElseThrow(() -> new IllegalStateException("Minecraft mod container is unavailable"));
        return new MinecraftVersion(version);
    }

    @Override
    public Environment environment() {
        return FMLEnvironment.getDist() == Dist.CLIENT
                ? Environment.CLIENT : Environment.DEDICATED_SERVER;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public Optional<String> modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString());
    }
}
