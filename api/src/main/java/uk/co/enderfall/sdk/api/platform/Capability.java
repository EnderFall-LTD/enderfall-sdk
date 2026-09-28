package uk.co.enderfall.sdk.api.platform;

/** Portable features that an adapter can explicitly advertise. */
public enum Capability {
    REGISTRIES,
    EVENTS,
    COMMANDS,
    CONFIGURATION,
    NETWORKING,
    DATA_GENERATION,
    PLAYER_ACTIONS,
    SYNCHRONIZED_SCREENS,
    GENERAL_CLIENT_SCREENS,
    CLIENT_COMMANDS,
    CUSTOM_RECIPES,
    CONTAINER_MENUS,
    STORAGE_CONTAINERS,
    BLOCK_STATES,
    SCHEDULED_BLOCK_TICKS,
    WATERLOGGED_BLOCKS,
    MIXED_LOADER_NETWORKING
}
