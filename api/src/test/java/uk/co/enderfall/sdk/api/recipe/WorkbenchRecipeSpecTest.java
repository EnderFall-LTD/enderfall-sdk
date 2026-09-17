package uk.co.enderfall.sdk.api.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.data.Ingredient;
import uk.co.enderfall.sdk.api.data.RecipeResult;

class WorkbenchRecipeSpecTest {
    private static final ResourceId CRYSTAL = ResourceId.of("demo", "crystal");

    @Test
    void requiresOneCountedIngredientPerPositionalSlot() {
        WorkbenchRecipeTypeRef type = new WorkbenchRecipeTypeRef(ResourceId.of("demo", "infusing"), 2);
        CountedIngredient ingredient = CountedIngredient.of(Ingredient.item(CRYSTAL), 4);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                new WorkbenchRecipeSpec(type, List.of(ingredient), new RecipeResult(CRYSTAL, 1)));

        assertEquals("Recipe demo:infusing requires exactly 2 positional ingredients, received 1",
                exception.getMessage());
    }

    @Test
    void validatesRecipeAndIngredientBoundsBeforeAdaptersSeeThem() {
        assertThrows(IllegalArgumentException.class, () ->
                new WorkbenchRecipeTypeRef(ResourceId.of("demo", "none"), 0));
        assertThrows(IllegalArgumentException.class, () ->
                CountedIngredient.of(Ingredient.item(CRYSTAL), 65));
    }
}
