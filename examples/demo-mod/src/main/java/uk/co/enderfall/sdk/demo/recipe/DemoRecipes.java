package uk.co.enderfall.sdk.demo.recipe;

import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.recipe.WorkbenchRecipeTypeRef;

/** Registers the demo's custom recipe types and their target-native serializers. */
public record DemoRecipes(WorkbenchRecipeTypeRef resonanceInfusing) {
    public static final int CRYSTAL_COST = 4;

    public static DemoRecipes register(ModContext context) {
        return new DemoRecipes(context.recipes().registerWorkbenchType("resonance_infusing", 3));
    }
}
