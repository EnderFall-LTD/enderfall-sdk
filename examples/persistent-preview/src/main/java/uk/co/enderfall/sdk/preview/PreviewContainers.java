package uk.co.enderfall.sdk.preview;

import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.registry.Registration;
import uk.co.enderfall.sdk.api.ui.ContainerSoundProfile;
import uk.co.enderfall.sdk.api.ui.InventoryQuickMoveRule;
import uk.co.enderfall.sdk.api.ui.InventorySlotSpec;
import uk.co.enderfall.sdk.api.ui.StorageContainerRef;
import uk.co.enderfall.sdk.api.ui.StorageContainerSpec;

/** General storage menus, deliberately separate from recipe workbenches. */
public final class PreviewContainers {
    private PreviewContainers() { }

    public static final Registration.Menus MENUS = Registration.menus("enderfall_persistent_preview");
    public static final StorageContainerRef CABINET = MENUS.container(
            "cabinet", "Storage Cabinet", PreviewBlocks.CABINET_STORAGE,
            menu -> {
                menu.openState(PreviewStorageCabinetBlock.OPEN)
                        .sounds(ContainerSoundProfile.BARREL)
                        .size(176, 184)
                        .playerInventory(7, 104)
                        .quickMove("cabinet", InventoryQuickMoveRule.PLAYER_MAIN,
                                InventoryQuickMoveRule.PLAYER_HOTBAR)
                        .quickMove(InventoryQuickMoveRule.PLAYER_MAIN, "cabinet")
                        .quickMove(InventoryQuickMoveRule.PLAYER_HOTBAR, "cabinet");
                cabinetSlots(menu);
            });

    private static void cabinetSlots(StorageContainerSpec.Builder menu) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int slot = column + row * 9;
                menu.slot(InventorySlotSpec.storage(slot, 7 + column * 18, 30 + row * 18, "cabinet"));
            }
        }
    }

    public static void register(ModContext context) {
        Registration.register(context, MENUS);
    }
}
