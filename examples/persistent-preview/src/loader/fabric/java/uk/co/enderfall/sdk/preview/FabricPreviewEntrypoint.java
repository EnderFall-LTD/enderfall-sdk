package uk.co.enderfall.sdk.preview;

import net.fabricmc.api.ModInitializer;
import uk.co.enderfall.sdk.runtime.fabric.v1_21_4.FabricPersistentPreviewBootstrap;
import uk.co.enderfall.sdk.runtime.fabric.v1_21_4.FabricConsumerBootstrap;

/** Development launcher glue, not consumer gameplay code. */
public final class FabricPreviewEntrypoint implements ModInitializer {
    @Override public void onInitialize() {
        if (Boolean.getBoolean("enderfall.persistence")) {
            FabricConsumerBootstrap.initialize("enderfall_persistent_preview", PersistentDemo.class.getName(), null);
        } else {
            FabricPersistentPreviewBootstrap.initialize("enderfall_persistent_preview", PersistentDemo.class.getName(), null);
        }
    }
}
