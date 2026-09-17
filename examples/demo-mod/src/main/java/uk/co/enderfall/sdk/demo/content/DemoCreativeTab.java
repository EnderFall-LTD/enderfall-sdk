package uk.co.enderfall.sdk.demo.content;

import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.registry.CreativeTabSpec;
import uk.co.enderfall.sdk.api.registry.ItemRef;

/** Keeps creative-tab composition separate from item and block definitions. */
public final class DemoCreativeTab {
    private DemoCreativeTab() {
    }

    public static void register(ModContext context, DemoItems items, DemoBlocks blocks) {
        context.creativeTabs().register("main", CreativeTabSpec.builder(
                        "tab.enderfall_sdk_demo.main", items.voidCrystal())
                .entry(items.voidCrystal())
                .entry(items.resonanceRod())
                .entry(items.resonanceCore())
                .entry(new ItemRef(blocks.enderAlloy().id()))
                .entry(new ItemRef(blocks.resonanceLamp().id()))
                .entry(new ItemRef(blocks.resonanceWorkbench().id()))
                .build());
    }
}
