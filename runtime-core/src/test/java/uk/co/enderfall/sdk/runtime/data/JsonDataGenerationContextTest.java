package uk.co.enderfall.sdk.runtime.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.data.Ingredient;
import uk.co.enderfall.sdk.api.data.ModelSpec;
import uk.co.enderfall.sdk.api.data.RecipeResult;
import uk.co.enderfall.sdk.api.data.ShapedRecipeSpec;
import uk.co.enderfall.sdk.api.data.TagSpec;
import uk.co.enderfall.sdk.api.platform.MinecraftVersion;
import uk.co.enderfall.sdk.api.recipe.CountedIngredient;
import uk.co.enderfall.sdk.api.recipe.WorkbenchRecipeSpec;
import uk.co.enderfall.sdk.api.recipe.WorkbenchRecipeTypeRef;

class JsonDataGenerationContextTest {
    @Test void generalVariantsAreCompleteAndStableAcrossTargets() {
        var open = uk.co.enderfall.sdk.api.block.BlockProperty.bool("open");
        var schema = uk.co.enderfall.sdk.api.block.BlockStateDefinition.builder().property(open, false).build();
        var old = new JsonDataGenerationContext(new MinecraftVersion("1.20.1"));
        var recent = new JsonDataGenerationContext(new MinecraftVersion("26.2"));
        var id = ResourceId.of("test_mod", "barrel");
        for (var context : List.of(old, recent)) {
            context.blockStates(id, schema, state -> ResourceId.of("test_mod", state.get(open) ? "block/open" : "block/closed"));
        }
        assertEquals(old.resources(), recent.resources());
        var json = old.resources().get("assets/test_mod/blockstates/barrel.json");
        assertTrue(json.contains("\"open=false\": { \"model\": \"test_mod:block/closed\" }"));
        assertTrue(json.contains("\"open=true\": { \"model\": \"test_mod:block/open\" }"));
        var failed = new JsonDataGenerationContext(new MinecraftVersion("1.20.1"));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> failed.blockStates(id, schema, state -> null));
        assertTrue(failed.resources().isEmpty());
    }
    @Test void sixWayVariantsIncludeVerticalRotationsAcrossTargets() {
        var block = ResourceId.of("test_mod", "barrel");
        var model = ResourceId.of("test_mod", "block/barrel");
        var old = new JsonDataGenerationContext(new MinecraftVersion("1.20.1"));
        var recent = new JsonDataGenerationContext(new MinecraftVersion("26.2"));
        old.sixWayBlockState(block, model); recent.sixWayBlockState(block, model);
        assertEquals(old.resources(), recent.resources());
        var json = old.resources().get("assets/test_mod/blockstates/barrel.json");
        assertTrue(json.contains("\"facing=up\": { \"model\": \"test_mod:block/barrel\", \"y\": 0, \"x\": 270"));
        assertTrue(json.contains("\"facing=down\": { \"model\": \"test_mod:block/barrel\", \"y\": 0, \"x\": 90"));
    }
    @Test void horizontalVariantsHaveMatchingShapeRotationsOnOldAndNewTargets() {
        var block = ResourceId.of("test_mod", "table");
        var model = ResourceId.of("test_mod", "block/table");
        var old = new JsonDataGenerationContext(new MinecraftVersion("1.20.1"));
        var recent = new JsonDataGenerationContext(new MinecraftVersion("26.2"));
        old.horizontalBlockState(block, model); recent.horizontalBlockState(block, model);
        assertEquals(old.resources(), recent.resources());
        String json = old.resources().get("assets/test_mod/blockstates/table.json");
        String[] directions = { "north", "east", "south", "west" };
        for (int i = 0; i < directions.length; i++) {
            assertTrue(json.contains("\"facing=" + directions[i] + "\": { \"model\": \"test_mod:block/table\", \"y\": " + i * 90));
        }
    }
    @Test void machineRecipesHaveOneStableFormatAcrossVersions() {
        var id = ResourceId.of("test_mod", "washing");
        var water = ResourceId.of("minecraft", "water");
        var recipe = new uk.co.enderfall.sdk.api.recipe.MachineRecipeSpec(id,
                List.of(new CountedIngredient(Ingredient.item(ResourceId.of("minecraft", "amethyst_shard")), 1)),
                List.of(uk.co.enderfall.sdk.api.recipe.FluidIngredient.tag(ResourceId.of("test_mod", "coolant"), 81000)),
                List.of(new RecipeResult(ResourceId.of("minecraft", "prismarine_crystals"), 1)),
                List.of(new uk.co.enderfall.sdk.api.fluid.FluidVolume(water, 40500)), 60);
        var old = new JsonDataGenerationContext(new MinecraftVersion("1.20.1"));
        var recent = new JsonDataGenerationContext(new MinecraftVersion("26.2"));
        old.machineRecipe(id, recipe); recent.machineRecipe(id, recipe);
        assertEquals(old.resources(), recent.resources());
        assertTrue(old.resources().get("data/test_mod/enderfall_machine/washing.json").contains("81000"));
    }
    @Test
    void changesOnlyVersionSpecificPathsAndKeys() {
        ResourceId hammer = ResourceId.of("test_mod", "hammer");
        ShapedRecipeSpec recipe = new ShapedRecipeSpec(List.of("II", " S"),
                Map.of('I', Ingredient.item(ResourceId.of("minecraft", "iron_ingot")),
                        'S', Ingredient.item(ResourceId.of("minecraft", "stick"))),
                new RecipeResult(hammer, 1));

        JsonDataGenerationContext oldVersion = new JsonDataGenerationContext(new MinecraftVersion("1.20.1"),
                "test_mod");
        oldVersion.shapedRecipe(hammer, recipe);
        oldVersion.tag(ResourceId.of("test_mod", "tools"),
                new TagSpec(TagSpec.Registry.ITEMS, List.of(hammer), false));
        oldVersion.blockModel(ResourceId.of("test_mod", "workbench"),
                new ModelSpec(ModelSpec.Kind.CUBE_ALL_BLOCK, ResourceId.of("minecraft", "block/oak_planks")));
        oldVersion.translation("en_gb", "item.test_mod.hammer", "Hammer");

        JsonDataGenerationContext newVersion = new JsonDataGenerationContext(new MinecraftVersion("1.21.4"),
                "test_mod");
        newVersion.shapedRecipe(hammer, recipe);
        newVersion.tag(ResourceId.of("test_mod", "tools"),
                new TagSpec(TagSpec.Registry.ITEMS, List.of(hammer), false));
        newVersion.blockModel(ResourceId.of("test_mod", "workbench"),
                new ModelSpec(ModelSpec.Kind.CUBE_ALL_BLOCK, ResourceId.of("minecraft", "block/oak_planks")));
        newVersion.translation("en_gb", "item.test_mod.hammer", "Hammer");

        assertTrue(oldVersion.resources().containsKey("data/test_mod/recipes/hammer.json"));
        assertTrue(oldVersion.resources().containsKey("data/test_mod/tags/items/tools.json"));
        assertTrue(oldVersion.resources().get("data/test_mod/recipes/hammer.json")
                .contains("{ \"item\": \"minecraft:iron_ingot\" }"));
        assertTrue(oldVersion.resources().containsKey("assets/test_mod/models/item/workbench.json"));
        assertTrue(newVersion.resources().containsKey("data/test_mod/recipe/hammer.json"));
        assertTrue(newVersion.resources().containsKey("data/test_mod/tags/item/tools.json"));
        assertTrue(newVersion.resources().get("data/test_mod/recipe/hammer.json")
                .contains("\"I\": \"minecraft:iron_ingot\""));
        assertTrue(newVersion.resources().containsKey("assets/test_mod/models/item/workbench.json"));
        assertTrue(newVersion.resources().containsKey("assets/test_mod/items/workbench.json"));
        assertTrue(newVersion.resources().containsKey("assets/test_mod/lang/en_gb.json"));
        assertEquals(new java.util.ArrayList<>(new java.util.TreeSet<>(newVersion.resources().keySet())),
                new java.util.ArrayList<>(newVersion.resources().keySet()));
    }

    @Test
    void writesTheSamePortableWorkbenchRecipeInEachNativeJsonShape() {
        ResourceId recipeId = ResourceId.of("test_mod", "resonance_core_infusing");
        WorkbenchRecipeTypeRef type = new WorkbenchRecipeTypeRef(
                ResourceId.of("test_mod", "resonance_infusing"), 2);
        WorkbenchRecipeSpec recipe = new WorkbenchRecipeSpec(type, List.of(
                CountedIngredient.of(Ingredient.item(ResourceId.of("test_mod", "void_crystal")), 4),
                CountedIngredient.of(Ingredient.item(ResourceId.of("minecraft", "echo_shard")), 1)),
                new RecipeResult(ResourceId.of("test_mod", "resonance_core"), 1));

        JsonDataGenerationContext oldVersion = new JsonDataGenerationContext(
                new MinecraftVersion("1.20.1"), "test_mod");
        oldVersion.workbenchRecipe(recipeId, recipe);
        String oldJson = oldVersion.resources().get("data/test_mod/recipes/resonance_core_infusing.json");

        JsonDataGenerationContext newVersion = new JsonDataGenerationContext(
                new MinecraftVersion("26.2"), "test_mod");
        newVersion.workbenchRecipe(recipeId, recipe);
        String newJson = newVersion.resources().get("data/test_mod/recipe/resonance_core_infusing.json");

        assertTrue(oldJson.contains("\"type\": \"test_mod:resonance_infusing\""));
        assertTrue(oldJson.contains("\"ingredient\": { \"item\": \"test_mod:void_crystal\" }"));
        assertTrue(oldJson.contains("\"item\": \"test_mod:resonance_core\""));
        assertTrue(newJson.contains("\"ingredient\": \"test_mod:void_crystal\""));
        assertTrue(newJson.contains("\"id\": \"test_mod:resonance_core\""));
    }
}
