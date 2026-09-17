package uk.co.enderfall.sdk.runtime.blockentity;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.fluid.FluidVolume;
import uk.co.enderfall.sdk.api.recipe.FluidIngredient;
import uk.co.enderfall.sdk.api.recipe.MachineRecipeSpec;

class MachineFluidPlanTest {
    private final ResourceId water = ResourceId.of("minecraft", "water");
    private final ResourceId lava = ResourceId.of("minecraft", "lava");
    private MachineRecipeSpec recipe(FluidIngredient ingredient, long output) {
        return new MachineRecipeSpec(ResourceId.of("test", "mixer"), List.of(), List.of(ingredient),
                List.of(), List.of(new FluidVolume(water, output)), 20);
    }
    private MachineFluidPlan.Tank tank(ResourceId fluid, long amount, long capacity) {
        return new MachineFluidPlan.Tank(capacity, amount == 0 ? Optional.empty() : Optional.of(new FluidVolume(fluid, amount)));
    }
    @Test void blockedOutputLeavesAllInputsUntouchedAndDoesNotMixFluids() {
        var inputs = List.of(tank(water, 1000, 1000));
        for (var output : List.of(tank(water, 950, 1000), tank(lava, 1, 1000))) {
            var result = MachineFluidPlan.plan(recipe(FluidIngredient.fluid(water, 500), 100), inputs, List.of(output), (tag, fluid) -> false);
            assertEquals(MachineFluidPlan.Status.OUTPUT_BLOCKED, result.status());
            assertEquals(inputs, result.inputs());
            assertEquals(List.of(output), result.outputs());
        }
    }
    @Test void tagMatchingAndReadyPlanDoNotMutateSource() {
        var tag = ResourceId.of("test", "coolants");
        var inputs = List.of(tank(water, 500, 1000));
        var outputs = List.of(tank(water, 0, 1000));
        var result = MachineFluidPlan.plan(recipe(FluidIngredient.tag(tag, 500), 100), inputs, outputs,
                (id, fluid) -> id.equals(tag) && fluid.equals(water));
        assertEquals(MachineFluidPlan.Status.READY, result.status());
        assertTrue(result.inputs().get(0).contents().isEmpty());
        assertEquals(100, result.outputs().get(0).contents().orElseThrow().amount());
        assertEquals(500, inputs.get(0).contents().orElseThrow().amount());
        assertTrue(outputs.get(0).contents().isEmpty());
        assertEquals(MachineFluidPlan.Status.MISSING_INPUT, MachineFluidPlan.plan(
                recipe(FluidIngredient.tag(tag, 500), 100), inputs, outputs, (a, b) -> false).status());
    }
    @Test void fullLongCapacityDoesNotOverflow() {
        var result = MachineFluidPlan.plan(recipe(FluidIngredient.fluid(water, 1), Long.MAX_VALUE),
                List.of(tank(water, 1, 1)), List.of(tank(water, 1, Long.MAX_VALUE)), (a, b) -> false);
        assertEquals(MachineFluidPlan.Status.OUTPUT_BLOCKED, result.status());
    }
}
