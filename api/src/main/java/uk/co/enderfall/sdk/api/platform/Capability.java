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
    CUSTOM_RECIPES,
    CONTAINER_MENUS,
    BLOCK_STATES,
    SCHEDULED_BLOCK_TICKS,
    WATERLOGGED_BLOCKS,
    MIXED_LOADER_NETWORKING
}
