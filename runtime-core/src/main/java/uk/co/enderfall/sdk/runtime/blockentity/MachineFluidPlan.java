package uk.co.enderfall.sdk.runtime.blockentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.fluid.FluidVolume;
import uk.co.enderfall.sdk.api.recipe.FluidIngredient;
import uk.co.enderfall.sdk.api.recipe.MachineRecipeSpec;

/** Pure positional planning. Inputs and outputs are distinct tanks. Does not mutate storage.
 * The owner must revalidate and commit this plan atomically with the item plan.
 */
public final class MachineFluidPlan {
    private MachineFluidPlan() { }
    public record Tank(long capacity, Optional<FluidVolume> contents) {
        public Tank {
            Objects.requireNonNull(contents, "contents");
            if (capacity <= 0 || contents.map(v -> v.amount() > capacity).orElse(false)) {
                throw new IllegalArgumentException("Invalid tank snapshot");
            }
        }
    }
    public enum Status { MISSING_INPUT, OUTPUT_BLOCKED, READY }
    public record Result(Status status, List<Tank> inputs, List<Tank> outputs) {
        public Result { inputs = List.copyOf(inputs); outputs = List.copyOf(outputs); }
    }

    /** Tag predicate receives (tag ID, actual fluid ID). Only consulted for tag ingredients. */
    public static Result plan(MachineRecipeSpec recipe, List<Tank> inputs, List<Tank> outputs,
                              BiPredicate<ResourceId, ResourceId> fluidInTag) {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(fluidInTag, "fluidInTag");
        inputs = List.copyOf(inputs); outputs = List.copyOf(outputs);
        if (inputs.size() != recipe.fluidInputs().size() || outputs.size() != recipe.fluidOutputs().size()) {
            throw new IllegalArgumentException("Fluid tank layout does not match recipe");
        }
        for (int i = 0; i < inputs.size(); i++) {
            var required = recipe.fluidInputs().get(i);
            var actual = inputs.get(i).contents();
            if (actual.isEmpty() || actual.get().amount() < required.amount()
                    || !(required.kind() == FluidIngredient.Kind.FLUID ? required.id().equals(actual.get().fluid())
                    : fluidInTag.test(required.id(), actual.get().fluid()))) {
                return new Result(Status.MISSING_INPUT, inputs, outputs);
            }
        }
        for (int i = 0; i < outputs.size(); i++) {
            var produced = recipe.fluidOutputs().get(i);
            var tank = outputs.get(i);
            long stored = tank.contents().map(FluidVolume::amount).orElse(0L);
            if (tank.contents().isPresent() && !tank.contents().get().fluid().equals(produced.fluid())
                    || produced.amount() > tank.capacity() - stored) {
                return new Result(Status.OUTPUT_BLOCKED, inputs, outputs);
            }
        }
        var nextInputs = new ArrayList<Tank>();
        for (int i = 0; i < inputs.size(); i++) {
            var tank = inputs.get(i);
            var actual = tank.contents().orElseThrow();
            long left = actual.amount() - recipe.fluidInputs().get(i).amount();
            nextInputs.add(new Tank(tank.capacity(), left == 0 ? Optional.empty()
                    : Optional.of(new FluidVolume(actual.fluid(), left))));
        }
        var nextOutputs = new ArrayList<Tank>();
        for (int i = 0; i < outputs.size(); i++) {
            var tank = outputs.get(i);
            var produced = recipe.fluidOutputs().get(i);
            nextOutputs.add(new Tank(tank.capacity(), Optional.of(new FluidVolume(produced.fluid(),
                    tank.contents().map(FluidVolume::amount).orElse(0L) + produced.amount()))));
        }
        return new Result(Status.READY, nextInputs, nextOutputs);
    }
}
