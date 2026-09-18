package uk.co.enderfall.sdk.preview;

import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.block.BlockDirection;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;
import uk.co.enderfall.sdk.api.blockentity.InventoryAccessMode;
import uk.co.enderfall.sdk.api.registry.*;

/** All block and block-item declarations live here; no loader code or build() calls. */
public final class PreviewBlocks {
    private PreviewBlocks() { }
    public static final Registration.Blocks BLOCKS = Registration.blocks("enderfall_persistent_preview");
    public static final BlockRef ORIENTATION_TEST = BLOCKS.block("orientation_test", PreviewOrientationBlock::new,
            p -> p.strength(2, 4).withItem());
    public static final BlockRef CONNECTING_TABLE = BLOCKS.block("connecting_table", PreviewConnectingTableBlock::new,
            p -> p.copyFrom(ResourceId.of("minecraft", "oak_planks")).strength(2, 4).withItem());
    public static final BlockRef WORKBENCH = BLOCKS.block("workbench", PreviewWorkbenchBlock::new, p -> p
            .copyFrom(ResourceId.of("minecraft", "oak_planks")).strength(2, 4));
    public static final BlockRef TIMED_WORKBENCH = BLOCKS.block("timed_workbench", p -> p
            .copyFrom(WORKBENCH).storage(PreviewBlocks.TIMED_STORAGE));
    public static final BlockRef FLUID_TANK = BLOCKS.block("fluid_tank", p -> p
            .strength(2, 4).storage(PreviewBlocks.TANK_STORAGE));
    public static final BlockRef STORAGE_CABINET = BLOCKS.block("storage_cabinet",
            PreviewStorageCabinetBlock::new,
            p -> p.copyFrom(ResourceId.of("minecraft", "barrel")).strength(2, 4)
                    .storage(PreviewBlocks.CABINET_STORAGE));

    public static final BlockEntitySpec WORKBENCH_STORAGE = Registration.storage(WORKBENCH,
            p -> p.inventorySlots(3).renderSlot(0).renderSlot(1).renderSlot(2));
    public static final BlockEntitySpec TIMED_STORAGE = Registration.storage(TIMED_WORKBENCH,
            p -> p.inventorySlots(4).animation("open").animation("close").menuAnimations("open", "close"));
    public static final BlockEntitySpec TANK_STORAGE = Registration.storage(FLUID_TANK,
            p -> p.tank(PreviewFluids.RESERVOIR).renderTank(PreviewFluids.RESERVOIR.name())
                    .serverTicker(PreviewFluids::tick));
    public static final BlockEntitySpec CABINET_STORAGE = Registration.storage(STORAGE_CABINET,
            p -> p.inventorySlots(27).inventoryAccess(access -> access
                    .insert(BlockDirection.UP)
                    .horizontalFaces(InventoryAccessMode.BOTH)
                    .extract(BlockDirection.DOWN)));

    public static void register(ModContext context) { Registration.register(context, BLOCKS); }
}
