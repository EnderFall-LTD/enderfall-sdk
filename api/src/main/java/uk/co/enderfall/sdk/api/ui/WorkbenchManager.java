package uk.co.enderfall.sdk.api.ui;

import java.util.UUID;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.annotation.CapabilityGated;
import uk.co.enderfall.sdk.api.annotation.Experimental;
import uk.co.enderfall.sdk.api.platform.Capability;

/** Registers and opens vanilla-container-backed portable workbenches. */
@Experimental("Container workbenches are being proven across the supported target matrix")
@CapabilityGated(Capability.CONTAINER_MENUS)
public interface WorkbenchManager {
    WorkbenchRef register(ResourceId id, WorkbenchSpec spec, WorkbenchCraftHandler handler);

    WorkbenchRef register(String path, WorkbenchSpec spec, WorkbenchCraftHandler handler);

    void open(UUID playerId, WorkbenchRef workbench);

    /** Opens a registered persistent workbench at an exact server-side position. */
    @Experimental("Persistent block-entity integration is under development")
    default void openAt(UUID playerId, WorkbenchRef workbench,
            uk.co.enderfall.sdk.api.blockentity.BlockLocation location) {
        throw new UnsupportedOperationException("Persistent workbenches are unavailable on this implementation");
    }
}
