package uk.co.enderfall.sdk.preview;

import uk.co.enderfall.sdk.api.EnderfallMod;
import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.registry.CreativeTabSpec;
import uk.co.enderfall.sdk.api.registry.ItemRef;

/** Portable gameplay entrypoint; deliberately separate from the normal multi-target demo. */
public final class PersistentDemo implements EnderfallMod {
    @Override public void initialize(ModContext context) {
        PreviewBlocks.register(context);
        var storage = PreviewBlocks.WORKBENCH_STORAGE;
        var timedStorage = PreviewBlocks.TIMED_STORAGE;
        var tankStorage = PreviewBlocks.TANK_STORAGE;
        PreviewRecipes.register(context, storage, timedStorage);
        PreviewGauge.register(context);
        PreviewContainers.register(context);
        var mixer = PreviewMachines.register(context);
        var item = new ItemRef(storage.block().id());
        context.creativeTabs().register("preview", CreativeTabSpec.builder("itemGroup.enderfall_persistent_preview", item)
                .entry(item).entry(new ItemRef(timedStorage.block().id()))
                .entry(new ItemRef(tankStorage.block().id())).entry(mixer)
                .entry(new ItemRef(PreviewBlocks.ORIENTATION_TEST.id()))
                .entry(new ItemRef(PreviewBlocks.AXIS_TEST.id()))
                .entry(new ItemRef(PreviewBlocks.CONNECTING_TABLE.id()))
                .entry(new ItemRef(PreviewBlocks.STORAGE_CABINET.id())).build());
        context.logger().info("Persistent workbench preview registered; use a disposable test world.");
    }
}
