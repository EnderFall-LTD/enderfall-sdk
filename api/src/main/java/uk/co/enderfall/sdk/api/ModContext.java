package uk.co.enderfall.sdk.api;

import uk.co.enderfall.sdk.api.command.CommandManager;
import uk.co.enderfall.sdk.api.config.ConfigManager;
import uk.co.enderfall.sdk.api.data.DataGenerationManager;
import uk.co.enderfall.sdk.api.event.EventBus;
import uk.co.enderfall.sdk.api.logging.ModLogger;
import uk.co.enderfall.sdk.api.network.NetworkManager;
import uk.co.enderfall.sdk.api.platform.CapabilitySet;
import uk.co.enderfall.sdk.api.platform.PlatformInfo;
import uk.co.enderfall.sdk.api.registry.BlockRegistrar;
import uk.co.enderfall.sdk.api.registry.CreativeTabRegistrar;
import uk.co.enderfall.sdk.api.registry.ItemRegistrar;

/** Services scoped to one consumer mod. */
public interface ModContext {
    String modId();

    ResourceId id(String path);

    PlatformInfo platform();

    CapabilitySet capabilities();

    ModLogger logger();

    ItemRegistrar items();

    BlockRegistrar blocks();

    CreativeTabRegistrar creativeTabs();

    EventBus events();

    CommandManager commands();

    ConfigManager configs();

    NetworkManager networking();

    DataGenerationManager dataGeneration();
}
