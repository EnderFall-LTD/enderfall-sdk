package uk.co.enderfall.sdk.runtime;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.api.gameplay.PlayerSnapshot;
import uk.co.enderfall.sdk.api.gameplay.PlayerInventorySlot;
import uk.co.enderfall.sdk.api.item.ItemDataKey;
import uk.co.enderfall.sdk.api.item.MutableItemData;
import uk.co.enderfall.sdk.api.network.PacketDirection;
import uk.co.enderfall.sdk.api.platform.CapabilitySet;
import uk.co.enderfall.sdk.api.platform.PlatformInfo;
import uk.co.enderfall.sdk.api.registry.BlockSpec;
import uk.co.enderfall.sdk.api.registry.CreativeTabSpec;
import uk.co.enderfall.sdk.api.registry.ItemSpec;
import uk.co.enderfall.sdk.api.recipe.WorkbenchRecipeTypeRef;
import uk.co.enderfall.sdk.api.ui.MenuState;

/** Internal boundary implemented once by each Minecraft/loader target. */
public interface PlatformAdapter {
    /** Must validate native persistent-block existence and inventory slot bounds before registering. */
    default void registerBlockEntityRenderer(uk.co.enderfall.sdk.api.registry.BlockRef block,
            uk.co.enderfall.sdk.api.render.BlockEntityRenderSpec spec) {
        throw new UnsupportedOperationException("Block-entity rendering is unavailable");
    }
    default boolean supportsMenuGauges() { return false; }
    default void bindTankMenu(uk.co.enderfall.sdk.api.registry.BlockRef block,
            uk.co.enderfall.sdk.api.fluid.FluidTankSpec tank,
            java.util.function.BiConsumer<java.util.UUID, uk.co.enderfall.sdk.api.ui.MenuStateSource> opener) {
        throw new UnsupportedOperationException("World tank screens are unavailable");
    }
    PlatformInfo platformInfo();

    CapabilitySet capabilities();

    Path commonConfigDirectory();

    Path serverConfigDirectory();

    void registerItem(ResourceId id, ItemSpec spec);

    void registerBlock(ResourceId id, BlockSpec spec, ItemSpec blockItemSpec);

    /** False until native registration, container ownership, and opening are wired for this target. */
    default boolean supportsPersistentWorkbenches() { return false; }
    default boolean supportsBlockPropertyCopy() { return false; }
    default boolean supportsBlockShapes() { return false; }
    default boolean supportsHorizontalFacing() { return false; }
    default boolean supportsSixWayFacing() { return false; }
    default boolean supportsAxisFacing() { return false; }
    default boolean supportsBlockStates() { return false; }
    default boolean supportsStateShapes() { return false; }
    default boolean supportsScheduledBlockTicks() { return false; }
    default boolean supportsWaterloggedBlocks() { return false; }
    default java.util.Optional<uk.co.enderfall.sdk.api.block.PortableBlockState> blockState(
            uk.co.enderfall.sdk.api.registry.BlockRef block,
            uk.co.enderfall.sdk.api.blockentity.BlockLocation location) {
        throw new UnsupportedOperationException("Portable block-state access is unavailable");
    }
    default boolean updateBlockState(uk.co.enderfall.sdk.api.registry.BlockRef block,
            uk.co.enderfall.sdk.api.blockentity.BlockLocation location,
            java.util.function.UnaryOperator<uk.co.enderfall.sdk.api.block.PortableBlockState> change) {
        throw new UnsupportedOperationException("Portable block-state access is unavailable");
    }
    default boolean supportsTimedWorkbenches() { return false; }
    default boolean supportsStorageContainers() { return false; }

    default void registerStorageContainer(PortableStorageContainerDefinition definition) {
        throw new UnsupportedOperationException("Storage containers are unavailable on this adapter");
    }

    default void openStorageContainer(UUID playerId, PortableStorageContainerDefinition definition,
            uk.co.enderfall.sdk.api.blockentity.BlockLocation location) {
        throw new UnsupportedOperationException("Storage containers are unavailable on this adapter");
    }

    default void registerPersistentBlock(ResourceId id, BlockSpec spec, ItemSpec itemSpec,
            uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec storage) {
        throw new UnsupportedOperationException("Persistent blocks are unavailable on this adapter");
    }

    /** Must reject missing or mismatched native storage definitions; must not use a temporary container. */
    default void registerPersistentWorkbench(PortableWorkbenchDefinition definition) {
        throw new UnsupportedOperationException("Persistent workbenches are unavailable on this adapter");
    }

    /** Must validate server thread, dimension, loaded chunk, owner identity, and player reach before opening. */
    default void openPersistentWorkbench(UUID playerId, PortableWorkbenchDefinition definition,
            uk.co.enderfall.sdk.api.blockentity.BlockLocation location) {
        throw new UnsupportedOperationException("Persistent workbenches are unavailable on this adapter");
    }

    void registerCreativeTab(ResourceId id, CreativeTabSpec spec);

    void registerCommand(CommandSpec command);

    default void registerWorkbenchRecipeType(WorkbenchRecipeTypeRef recipeType) {
        throw new UnsupportedOperationException("Custom recipes are unavailable on this adapter");
    }

    default void registerWorkbench(PortableWorkbenchDefinition definition) {
        throw new UnsupportedOperationException("Container workbenches are unavailable on this adapter");
    }

    default void openWorkbench(UUID playerId, PortableWorkbenchDefinition definition) {
        throw new UnsupportedOperationException("Container workbenches are unavailable on this adapter");
    }

    void registerPayload(ResourceId id, PacketDirection direction, int maximumBytes, PayloadReceiver receiver);

    void sendToServer(ResourceId id, byte[] payload);

    void sendToPlayer(java.util.UUID playerId, ResourceId id, byte[] payload);

    void sendToAll(ResourceId id, byte[] payload);

    /** Players currently connected to this physical server; empty on clients and before server start. */
    default java.util.Collection<java.util.UUID> connectedPlayers() {
        return java.util.List.of();
    }

    default Optional<PlayerSnapshot> playerSnapshot(UUID playerId) {
        return Optional.empty();
    }

    default int countPlayerItem(UUID playerId, ResourceId itemId) {
        throw new UnsupportedOperationException("Player inventory access is unavailable on this adapter");
    }

    default boolean consumePlayerItems(UUID playerId, Map<ResourceId, Integer> items) {
        throw new UnsupportedOperationException("Player inventory access is unavailable on this adapter");
    }

    default void givePlayerItem(UUID playerId, ResourceId itemId, int amount) {
        throw new UnsupportedOperationException("Player inventory access is unavailable on this adapter");
    }

    default <T> Optional<T> playerItemData(UUID playerId, PlayerInventorySlot slot,
            ResourceId expectedItemId, ItemDataKey<T> key) {
        throw new UnsupportedOperationException("Player item data is unavailable on this adapter");
    }

    default boolean updatePlayerItemData(UUID playerId, PlayerInventorySlot slot,
            ResourceId expectedItemId, Consumer<MutableItemData> update) {
        throw new UnsupportedOperationException("Player item data is unavailable on this adapter");
    }

    default void sendPlayerMessage(UUID playerId, String message, boolean actionBar) {
        throw new UnsupportedOperationException("Player feedback is unavailable on this adapter");
    }

    default void healPlayer(UUID playerId, double amount) {
        throw new UnsupportedOperationException("Player healing is unavailable on this adapter");
    }

    default void addPlayerExperience(UUID playerId, int points) {
        throw new UnsupportedOperationException("Player experience is unavailable on this adapter");
    }

    default void showMenu(PortableMenuView view, java.util.function.Consumer<String> actionSender,
                          Runnable closeSender) {
        throw new UnsupportedOperationException("Synchronized screens are unavailable on this adapter");
    }

    default void updateMenu(long sessionId, MenuState state) {
        throw new UnsupportedOperationException("Synchronized screens are unavailable on this adapter");
    }

    default void closeMenu(long sessionId) {
        throw new UnsupportedOperationException("Synchronized screens are unavailable on this adapter");
    }
}
