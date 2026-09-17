package uk.co.enderfall.sdk.api.ui;

import java.util.UUID;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.annotation.CapabilityGated;
import uk.co.enderfall.sdk.api.annotation.Experimental;
import uk.co.enderfall.sdk.api.platform.Capability;

/** Registration and server-owned session API for synchronized custom screens. */
@Experimental("State-driven screens are experimental; inventory-backed workbenches use WorkbenchManager")
@CapabilityGated(Capability.SYNCHRONIZED_SCREENS)
public interface MenuManager {
    MenuRef register(ResourceId id, MenuSpec spec, MenuActionHandler handler);

    MenuRef register(String path, MenuSpec spec, MenuActionHandler handler);

    void open(UUID playerId, MenuRef menu, MenuState initialState);

    /**
     * Binds a persistent block's ordinary interaction to a read-only live tank screen.
     * Register after the block and menu, before initialization finishes. State uses
     * tank.fluid, tank.amount, tank.capacity and tank.percent. Bucket use is preserved.
     * The screen closes on unload, removal, death, spectator mode or distance over eight blocks.
     */
    @Experimental("World tank screen binding")
    default void bindTank(MenuRef menu, uk.co.enderfall.sdk.api.registry.BlockRef block,
                          uk.co.enderfall.sdk.api.fluid.FluidTankSpec tank) {
        throw new UnsupportedOperationException("World tank screens are unavailable");
    }

    /**
     * Opens a server-owned live view. Polls on server END ticks and only transmits changed state.
     * Interval is 1-1200 ticks. Call on the server thread; the source must validate player access.
     * An initially empty source leaves the current menu unchanged. Later empty/failed reads close it.
     */
    @Experimental("Live menu state binding")
    default void openLive(UUID playerId, MenuRef menu, int intervalTicks, MenuStateSource source) {
        throw new UnsupportedOperationException("Live menu state binding is unavailable");
    }

    void update(UUID playerId, MenuState state);

    void close(UUID playerId);

    boolean isOpen(UUID playerId, MenuRef menu);
}
