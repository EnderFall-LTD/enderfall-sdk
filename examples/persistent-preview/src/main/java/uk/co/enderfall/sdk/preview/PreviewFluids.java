package uk.co.enderfall.sdk.preview;

import java.util.Map;
import uk.co.enderfall.sdk.api.blockentity.BlockEntityState;
import uk.co.enderfall.sdk.api.fluid.FluidFace;
import uk.co.enderfall.sdk.api.fluid.FluidPortMode;
import uk.co.enderfall.sdk.api.fluid.FluidTankSpec;
import uk.co.enderfall.sdk.api.fluid.FluidVolume;

/** A four-bucket reservoir that pumps down at one bucket per second at 20 TPS. */
public final class PreviewFluids {
    private PreviewFluids() { }
    public static final FluidTankSpec RESERVOIR = new FluidTankSpec("reservoir", 4 * FluidVolume.BUCKET,
            Map.of(FluidFace.UP, FluidPortMode.INPUT, FluidFace.DOWN, FluidPortMode.OUTPUT,
                    FluidFace.NORTH, FluidPortMode.BOTH, FluidFace.SOUTH, FluidPortMode.BOTH,
                    FluidFace.EAST, FluidPortMode.BOTH, FluidFace.WEST, FluidPortMode.BOTH));

    public static void tick(BlockEntityState state) {
        state.pushFluid(RESERVOIR, FluidFace.DOWN, FluidVolume.BUCKET / 20, false);
    }
}
