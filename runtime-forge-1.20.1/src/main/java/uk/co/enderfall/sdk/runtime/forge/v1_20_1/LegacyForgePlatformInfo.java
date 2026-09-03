package uk.co.enderfall.sdk.runtime.forge.v1_20_1;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import uk.co.enderfall.sdk.api.platform.Environment;
import uk.co.enderfall.sdk.api.platform.Loader;
import uk.co.enderfall.sdk.api.platform.MinecraftVersion;
import uk.co.enderfall.sdk.api.platform.PlatformInfo;

final class LegacyForgePlatformInfo implements PlatformInfo {
    private final Loader loader = readLoader();

    @Override public Loader loader() { return loader; }
    @Override public MinecraftVersion minecraftVersion() { return new MinecraftVersion("1.20.1"); }
    @Override public Environment environment() {
        return FMLEnvironment.dist == Dist.CLIENT ? Environment.CLIENT : Environment.DEDICATED_SERVER;
    }
    @Override public boolean isModLoaded(String modId) { return ModList.get().isLoaded(modId); }
    @Override public Optional<String> modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString());
    }

    private static Loader readLoader() {
        try (InputStream input = LegacyForgePlatformInfo.class.getClassLoader()
                .getResourceAsStream("META-INF/enderfall-sdk-loader")) {
            if (input == null) {
                throw new IllegalStateException("EnderFall loader marker is missing");
            }
            return Loader.valueOf(new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read EnderFall loader marker", exception);
        }
    }
}
