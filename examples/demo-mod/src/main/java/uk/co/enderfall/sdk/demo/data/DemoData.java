package uk.co.enderfall.sdk.demo.data;

import java.util.List;
import java.util.Map;
import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.data.Ingredient;
import uk.co.enderfall.sdk.api.data.RecipeResult;
import uk.co.enderfall.sdk.api.data.ShapedRecipeSpec;
import uk.co.enderfall.sdk.api.data.ShapelessRecipeSpec;
import uk.co.enderfall.sdk.api.data.TagSpec;
import uk.co.enderfall.sdk.api.recipe.CountedIngredient;
import uk.co.enderfall.sdk.api.recipe.WorkbenchRecipeSpec;
import uk.co.enderfall.sdk.demo.content.DemoBlocks;
import uk.co.enderfall.sdk.demo.content.DemoItems;
import uk.co.enderfall.sdk.demo.recipe.DemoRecipes;

/** Recipes, loot, tags, and language entries are generated from portable Java. */
public final class DemoData {
    private DemoData() {
    }

    public static void register(ModContext context, DemoItems items, DemoBlocks blocks, DemoRecipes recipes) {
        context.dataGeneration().register(data -> {
            data.shapelessRecipe(context.id("void_crystal"), new ShapelessRecipeSpec(
                    List.of(Ingredient.item(ResourceId.of("minecraft", "ender_pearl")),
                            Ingredient.item(ResourceId.of("minecraft", "amethyst_shard")),
                            Ingredient.item(ResourceId.of("minecraft", "echo_shard"))),
                    new RecipeResult(items.voidCrystal().id(), 2)));
            data.shapedRecipe(context.id("resonance_rod"), new ShapedRecipeSpec(
                    List.of("  C", " A ", "S  "),
                    Map.of('C', Ingredient.item(items.voidCrystal().id()),
                            'A', Ingredient.item(ResourceId.of("minecraft", "amethyst_shard")),
                            'S', Ingredient.item(ResourceId.of("minecraft", "stick"))),
                    new RecipeResult(items.resonanceRod().id(), 1)));
            data.shapedRecipe(context.id("ender_alloy_block"), new ShapedRecipeSpec(
                    List.of("CCC", "CCC", "CCC"),
                    Map.of('C', Ingredient.item(items.voidCrystal().id())),
                    new RecipeResult(blocks.enderAlloy().id(), 1)));
            data.shapelessRecipe(context.id("resonance_lamp"), new ShapelessRecipeSpec(
                    List.of(Ingredient.item(blocks.enderAlloy().id()),
                            Ingredient.item(items.voidCrystal().id()),
                            Ingredient.item(ResourceId.of("minecraft", "glowstone_dust"))),
                    new RecipeResult(blocks.resonanceLamp().id(), 1)));
            data.shapedRecipe(context.id("resonance_workbench"), new ShapedRecipeSpec(
                    List.of("CLC", "AAA", "AAA"),
                    Map.of('C', Ingredient.item(items.voidCrystal().id()),
                            'L', Ingredient.item(blocks.resonanceLamp().id()),
                            'A', Ingredient.item(blocks.enderAlloy().id())),
                    new RecipeResult(blocks.resonanceWorkbench().id(), 1)));
            data.workbenchRecipe(context.id("resonance_core_infusing"), new WorkbenchRecipeSpec(
                    recipes.resonanceInfusing(),
                    List.of(
                            CountedIngredient.of(Ingredient.item(items.voidCrystal().id()), DemoRecipes.CRYSTAL_COST),
                            CountedIngredient.of(Ingredient.item(blocks.enderAlloy().id()), 1),
                            CountedIngredient.of(Ingredient.item(ResourceId.of("minecraft", "echo_shard")), 1)),
                    new RecipeResult(items.resonanceCore().id(), 1)));

            data.selfDrop(blocks.enderAlloy().id());
            data.selfDrop(blocks.resonanceLamp().id());
            data.selfDrop(blocks.resonanceWorkbench().id());
            data.tag(context.id("demo_items"), new TagSpec(TagSpec.Registry.ITEMS,
                    List.of(items.voidCrystal().id(), items.resonanceRod().id(),
                            items.resonanceCore().id(),
                            blocks.enderAlloy().id(), blocks.resonanceLamp().id(),
                            blocks.resonanceWorkbench().id()), false));
            data.tag(context.id("demo_blocks"), new TagSpec(TagSpec.Registry.BLOCKS,
                    List.of(blocks.enderAlloy().id(), blocks.resonanceLamp().id(),
                            blocks.resonanceWorkbench().id()), false));

            translations(data, "en_gb");
            translations(data, "en_us");
        });
    }

    private static void translations(uk.co.enderfall.sdk.api.data.DataGenerationContext data, String locale) {
        data.translation(locale, "item.enderfall_sdk_demo.void_crystal", "Void Crystal");
        data.translation(locale, "item.enderfall_sdk_demo.resonance_rod", "Resonance Rod");
        data.translation(locale, "item.enderfall_sdk_demo.resonance_core", "Resonance Core");
        data.translation(locale, "block.enderfall_sdk_demo.ender_alloy_block", "Ender Alloy Block");
        data.translation(locale, "block.enderfall_sdk_demo.resonance_lamp", "Resonance Lamp");
        data.translation(locale, "block.enderfall_sdk_demo.resonance_workbench", "Resonance Workbench");
        data.translation(locale, "tab.enderfall_sdk_demo.main", "EnderFall SDK Demo");
        data.translation(locale, "tag.item.enderfall_sdk_demo.demo_items", "EnderFall Demo Items");
        data.translation(locale, "tag.block.enderfall_sdk_demo.demo_blocks", "EnderFall Demo Blocks");
    }
}
