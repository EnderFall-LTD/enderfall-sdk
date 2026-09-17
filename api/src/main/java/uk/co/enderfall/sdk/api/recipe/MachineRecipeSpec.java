package uk.co.enderfall.sdk.api.recipe;

import java.util.List;
import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.annotation.Experimental;
import uk.co.enderfall.sdk.api.data.RecipeResult;
import uk.co.enderfall.sdk.api.fluid.FluidVolume;

/** Immutable positional machine recipe. Registration/native serializers are separate. */
@Experimental("Combined machine recipes")
public record MachineRecipeSpec(ResourceId type, List<CountedIngredient> itemInputs,
        List<FluidIngredient> fluidInputs, List<RecipeResult> itemOutputs,
        List<FluidVolume> fluidOutputs, int durationTicks) {
    public MachineRecipeSpec {
        Objects.requireNonNull(type, "type");
        itemInputs = List.copyOf(itemInputs);
        fluidInputs = List.copyOf(fluidInputs);
        itemOutputs = List.copyOf(itemOutputs);
        fluidOutputs = List.copyOf(fluidOutputs);
        if (itemInputs.size() > 16 || itemOutputs.size() > 16 || fluidInputs.size() > 16 || fluidOutputs.size() > 16) {
            throw new IllegalArgumentException("At most 16 entries per machine recipe section");
        }
        if (itemInputs.isEmpty() && fluidInputs.isEmpty()) throw new IllegalArgumentException("Recipe needs an input");
        if (itemOutputs.isEmpty() && fluidOutputs.isEmpty()) throw new IllegalArgumentException("Recipe needs an output");
        if (durationTicks < 1 || durationTicks > 1_728_000) throw new IllegalArgumentException("Duration must be 1..1728000 ticks");
    }
}
