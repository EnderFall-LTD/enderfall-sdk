package uk.co.enderfall.sdk.preview;

import java.util.List;
import java.util.Map;
import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;
import uk.co.enderfall.sdk.api.fluid.*;
import uk.co.enderfall.sdk.api.registry.*;
import uk.co.enderfall.sdk.api.ui.WorkbenchSpec;

/** Combined item/fluid processing example. Supply water with a tank above the mixer. */
public final class PreviewMachines {
    private PreviewMachines() { }
    public static final FluidTankSpec INPUT = new FluidTankSpec("coolant", 4 * FluidVolume.BUCKET,
            Map.of(FluidFace.UP, FluidPortMode.INPUT));
    public static final FluidTankSpec OUTPUT = new FluidTankSpec("effluent", 4 * FluidVolume.BUCKET,
            Map.of(FluidFace.DOWN, FluidPortMode.OUTPUT));
    public static ItemRef register(ModContext context) {
        var storage = BlockEntitySpec.builder(new BlockRef(context.id("mixer"))).inventorySlots(2)
                .tank(INPUT).tank(OUTPUT).renderTank(INPUT.name()).renderTank(OUTPUT.name())
                .serverTicker(state -> state.pushFluid(OUTPUT, FluidFace.DOWN, FluidVolume.BUCKET / 20, false)).build();
        context.blocks().registerPersistentWithItem("mixer", BlockSpec.builder().strength(2, 4).build(),
                ItemSpec.builder().build(), storage);
        var type = context.recipes().registerMachineType("washing", 1);
        context.workbenches().register("mixer_menu", WorkbenchSpec.builder("Crystal Washer", type)
                .persistentMachine(storage, List.of(INPUT.name()), List.of(OUTPUT.name())).build(), craft -> { });
        return new ItemRef(storage.block().id());
    }
}
