package uk.co.enderfall.sdk.runtime.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.data.Ingredient;
import uk.co.enderfall.sdk.api.data.RecipeResult;
import uk.co.enderfall.sdk.api.data.ShapedRecipeSpec;
import uk.co.enderfall.sdk.api.data.TagSpec;
import uk.co.enderfall.sdk.api.platform.MinecraftVersion;

class JsonDataGenerationContextTest {
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
        oldVersion.translation("en_gb", "item.test_mod.hammer", "Hammer");

        JsonDataGenerationContext newVersion = new JsonDataGenerationContext(new MinecraftVersion("1.21.4"),
                "test_mod");
        newVersion.shapedRecipe(hammer, recipe);
        newVersion.tag(ResourceId.of("test_mod", "tools"),
                new TagSpec(TagSpec.Registry.ITEMS, List.of(hammer), false));
        newVersion.translation("en_gb", "item.test_mod.hammer", "Hammer");

        assertTrue(oldVersion.resources().containsKey("data/test_mod/recipes/hammer.json"));
        assertTrue(oldVersion.resources().containsKey("data/test_mod/tags/items/tools.json"));
        assertTrue(newVersion.resources().containsKey("data/test_mod/recipe/hammer.json"));
        assertTrue(newVersion.resources().containsKey("data/test_mod/tags/item/tools.json"));
        assertTrue(newVersion.resources().containsKey("assets/test_mod/lang/en_gb.json"));
        assertEquals(new java.util.ArrayList<>(new java.util.TreeSet<>(newVersion.resources().keySet())),
                new java.util.ArrayList<>(newVersion.resources().keySet()));
    }
}
