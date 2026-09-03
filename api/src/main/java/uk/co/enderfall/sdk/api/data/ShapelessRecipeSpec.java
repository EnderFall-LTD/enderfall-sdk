package uk.co.enderfall.sdk.api.data;

import java.util.List;
import java.util.Objects;

public record ShapelessRecipeSpec(List<Ingredient> ingredients, RecipeResult result) {
    public ShapelessRecipeSpec {
        ingredients = List.copyOf(ingredients);
        Objects.requireNonNull(result, "result");
        if (ingredients.isEmpty() || ingredients.size() > 9) {
            throw new IllegalArgumentException("A shapeless recipe needs one to nine ingredients");
        }
    }
}
