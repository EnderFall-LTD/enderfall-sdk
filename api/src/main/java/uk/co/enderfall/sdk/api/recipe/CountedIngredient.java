package uk.co.enderfall.sdk.api.recipe;

import java.util.Objects;
import uk.co.enderfall.sdk.api.data.Ingredient;

/** One positional custom-recipe ingredient and the amount consumed per craft. */
public record CountedIngredient(Ingredient ingredient, int count) {
    public CountedIngredient {
        Objects.requireNonNull(ingredient, "ingredient");
        if (count < 1 || count > 64) {
            throw new IllegalArgumentException("Ingredient count must be between 1 and 64");
        }
    }

    public static CountedIngredient of(Ingredient ingredient, int count) {
        return new CountedIngredient(ingredient, count);
    }
}
