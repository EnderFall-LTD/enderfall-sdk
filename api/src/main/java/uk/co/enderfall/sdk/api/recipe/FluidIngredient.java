package uk.co.enderfall.sdk.api.recipe;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/** Fluid identity or fluid tag plus quantity, in SDK units (81,000 per bucket). */
@Experimental("Combined machine recipes")
public record FluidIngredient(ResourceId id, Kind kind, long amount) {
    public enum Kind { FLUID, TAG }
    public FluidIngredient {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        if (amount <= 0) throw new IllegalArgumentException("Fluid ingredient amount must be positive");
    }
    public static FluidIngredient fluid(ResourceId id, long amount) { return new FluidIngredient(id, Kind.FLUID, amount); }
    public static FluidIngredient tag(ResourceId id, long amount) { return new FluidIngredient(id, Kind.TAG, amount); }
}
