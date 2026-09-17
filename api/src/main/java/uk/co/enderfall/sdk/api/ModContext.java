package uk.co.enderfall.sdk.api;

import uk.co.enderfall.sdk.api.command.CommandManager;
import uk.co.enderfall.sdk.api.annotation.CapabilityGated;
import uk.co.enderfall.sdk.api.annotation.Experimental;
import uk.co.enderfall.sdk.api.config.ConfigManager;
import uk.co.enderfall.sdk.api.data.DataGenerationManager;
import uk.co.enderfall.sdk.api.event.EventBus;
import uk.co.enderfall.sdk.api.gameplay.PlayerManager;
import uk.co.enderfall.sdk.api.logging.ModLogger;
import uk.co.enderfall.sdk.api.network.NetworkManager;
import uk.co.enderfall.sdk.api.platform.CapabilitySet;
import uk.co.enderfall.sdk.api.platform.PlatformInfo;
import uk.co.enderfall.sdk.api.registry.BlockRegistrar;
import uk.co.enderfall.sdk.api.registry.CreativeTabRegistrar;
import uk.co.enderfall.sdk.api.registry.ItemRegistrar;
import uk.co.enderfall.sdk.api.recipe.RecipeRegistrar;
import uk.co.enderfall.sdk.api.ui.MenuManager;
import uk.co.enderfall.sdk.api.ui.WorkbenchManager;
import uk.co.enderfall.sdk.api.ui.StorageContainerManager;

/** Services scoped to one consumer mod. */
public interface ModContext {
    String modId();

    ResourceId id(String path);

    PlatformInfo platform();

    CapabilitySet capabilities();

    ModLogger logger();

    ItemRegistrar items();

    BlockRegistrar blocks();

    @Experimental("Portable server-world block-state access")
    @CapabilityGated(uk.co.enderfall.sdk.api.platform.Capability.BLOCK_STATES)
    uk.co.enderfall.sdk.api.block.BlockStateManager blockStates();

    CreativeTabRegistrar creativeTabs();

    EventBus events();

    PlayerManager players();

    @Experimental("Inventory slots and block-entity ownership are not part of the first synchronized-screen slice")
    @CapabilityGated(uk.co.enderfall.sdk.api.platform.Capability.SYNCHRONIZED_SCREENS)
    MenuManager menus();

    @Experimental("Portable recipe serializers are being proven across the supported target matrix")
    @CapabilityGated(uk.co.enderfall.sdk.api.platform.Capability.CUSTOM_RECIPES)
    RecipeRegistrar recipes();

    @Experimental("Container workbenches are being proven across the supported target matrix")
    @CapabilityGated(uk.co.enderfall.sdk.api.platform.Capability.CONTAINER_MENUS)
    WorkbenchManager workbenches();

    /** General block-owned inventories such as chests, cabinets, crates and barrels. */
    @Experimental("General storage containers are being proven across the supported target matrix")
    @CapabilityGated(uk.co.enderfall.sdk.api.platform.Capability.STORAGE_CONTAINERS)
    StorageContainerManager containers();

    CommandManager commands();

    ConfigManager configs();

    NetworkManager networking();

    DataGenerationManager dataGeneration();
}
