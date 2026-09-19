package uk.co.enderfall.sdk.preview;

import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;
import uk.co.enderfall.sdk.api.registry.Registration;

/** Custom recipe type and persistent menu binding; the matching JSON is a preview fixture. */
public final class PreviewRecipes {
    private PreviewRecipes() { }
    public static void register(ModContext context, BlockEntitySpec storage, BlockEntitySpec timedStorage) {
        var recipes = Registration.recipes(context.modId());
        var menus = Registration.menus(context.modId());
        var assembly = recipes.workbench("assembly", 3);
        menus.workbench("workbench_menu", "Persistent Workbench", assembly,
                p -> p.persistent(storage).recipeBrowser(),
                craft -> context.logger().info("Persistent preview craft: {}", craft.recipeId()));
        menus.workbench("timed_workbench_menu", "Timed Workbench", assembly,
                p -> p.persistentTimed(timedStorage, 100), craft -> { });
        Registration.register(context, menus, recipes);
    }
}
