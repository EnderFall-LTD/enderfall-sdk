package uk.co.enderfall.sdk.api.client.ui;

import uk.co.enderfall.sdk.api.ui.StorageContainerRef;
import uk.co.enderfall.sdk.api.blockentity.BlockEntityInt;

/** Geometry and identity of an open server-authoritative portable inventory menu. */
public interface InventoryScreenContext extends ClientScreenContext {
    StorageContainerRef container();

    String title();

    int left();

    int top();

    int menuWidth();

    int menuHeight();

    /** Current server-synchronized value of a field explicitly exposed by the container spec. */
    int value(BlockEntityInt field);
}
