package uk.co.enderfall.sdk.api.ui;

import java.util.UUID;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.annotation.CapabilityGated;
import uk.co.enderfall.sdk.api.annotation.Experimental;
import uk.co.enderfall.sdk.api.blockentity.BlockLocation;
import uk.co.enderfall.sdk.api.platform.Capability;

/** Registration and server-side opening for block-owned general storage menus. */
@Experimental("General storage containers are being proven across the supported target matrix")
@CapabilityGated(Capability.STORAGE_CONTAINERS)
public interface StorageContainerManager {
    StorageContainerRef register(ResourceId id, StorageContainerSpec spec);

    StorageContainerRef register(String path, StorageContainerSpec spec);

    void openAt(UUID playerId, StorageContainerRef container, BlockLocation location);
}
