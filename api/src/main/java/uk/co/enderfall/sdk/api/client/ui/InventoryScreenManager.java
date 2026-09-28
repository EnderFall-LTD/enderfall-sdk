package uk.co.enderfall.sdk.api.client.ui;

import java.util.function.Supplier;
import uk.co.enderfall.sdk.api.annotation.Experimental;
import uk.co.enderfall.sdk.api.annotation.CapabilityGated;
import uk.co.enderfall.sdk.api.platform.Capability;
import uk.co.enderfall.sdk.api.ui.StorageContainerRef;

/** Client-only binding between a synchronized storage menu and its portable presentation. */
@Experimental("Authored portable inventory screens")
@CapabilityGated(Capability.AUTHORED_INVENTORY_SCREENS)
public interface InventoryScreenManager {
    void register(StorageContainerRef container,
            Supplier<? extends PortableInventoryScreen> factory);
}
