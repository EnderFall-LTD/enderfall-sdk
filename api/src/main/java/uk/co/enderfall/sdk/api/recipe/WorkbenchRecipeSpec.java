package uk.co.enderfall.sdk.api.recipe;

import java.util.List;
import java.util.Objects;
import uk.co.enderfall.sdk.api.data.RecipeResult;

/** Data-driven positional recipe consumed by an EnderFall workbench menu. */
public record WorkbenchRecipeSpec(WorkbenchRecipeTypeRef type, List<CountedIngredient> ingredients,
                                  RecipeResult result) {
    public WorkbenchRecipeSpec {
        Objects.requireNonNull(type, "type");
        ingredients = List.copyOf(Objects.requireNonNull(ingredients, "ingredients"));
        Objects.requireNonNull(result, "result");
        if (ingredients.size() != type.inputSlots()) {
            throw new IllegalArgumentException("Recipe " + type.id() + " requires exactly "
                    + type.inputSlots() + " positional ingredients, received " + ingredients.size());
        }
    }
}
