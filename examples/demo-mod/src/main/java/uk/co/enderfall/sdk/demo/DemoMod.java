package uk.co.enderfall.sdk.demo;

import uk.co.enderfall.sdk.api.EnderfallMod;
import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.demo.command.DemoCommands;
import uk.co.enderfall.sdk.demo.config.DemoConfig;
import uk.co.enderfall.sdk.demo.content.DemoBlocks;
import uk.co.enderfall.sdk.demo.content.DemoCreativeTab;
import uk.co.enderfall.sdk.demo.content.DemoItems;
import uk.co.enderfall.sdk.demo.data.DemoData;
import uk.co.enderfall.sdk.demo.event.DemoEvents;
import uk.co.enderfall.sdk.demo.gameplay.DemoGameplay;
import uk.co.enderfall.sdk.demo.network.DemoNetworking;
import uk.co.enderfall.sdk.demo.recipe.DemoRecipes;
import uk.co.enderfall.sdk.demo.workbench.DemoWorkbench;

/** Coordinates the demo while each feature family owns its registrations. */
public final class DemoMod implements EnderfallMod {
    @Override
    public void initialize(ModContext context) {
        DemoItems items = DemoItems.register(context);
        DemoBlocks blocks = DemoBlocks.register(context);
        DemoCreativeTab.register(context, items, blocks);

        DemoConfig config = DemoConfig.register(context);
        DemoNetworking networking = DemoNetworking.register(context);
        DemoRecipes recipes = DemoRecipes.register(context);
        DemoGameplay gameplay = DemoGameplay.register(context, config, items, blocks);
        DemoWorkbench workbench = DemoWorkbench.register(context, recipes, gameplay);
        gameplay.attachWorkbench(workbench);
        DemoCommands.register(context, config, networking, gameplay);
        DemoEvents.register(context, config);
        DemoData.register(context, items, blocks, recipes);

        context.logger().info("EnderFall SDK demo initialized on {}", context.platform().targetId());
    }
}
