package uk.co.enderfall.sdk.preview;

import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.registry.Registration;
import uk.co.enderfall.sdk.api.ui.ContainerSoundProfile;
import uk.co.enderfall.sdk.api.ui.StorageContainerRef;

/** General storage menus, deliberately separate from recipe workbenches. */
public final class PreviewContainers {
    private PreviewContainers() { }

    public static final Registration.Menus MENUS = Registration.menus("enderfall_persistent_preview");
    public static final StorageContainerRef CABINET = MENUS.container(
            "cabinet", "Storage Cabinet", PreviewBlocks.CABINET_STORAGE,
            menu -> menu.openState(PreviewStorageCabinetBlock.OPEN)
                    .sounds(ContainerSoundProfile.BARREL));

    public static void register(ModContext context) {
        Registration.register(context, MENUS);
    }
}
