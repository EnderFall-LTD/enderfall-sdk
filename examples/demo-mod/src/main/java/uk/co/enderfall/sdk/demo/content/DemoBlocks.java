package uk.co.enderfall.sdk.demo.content;

import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.registry.BlockRef;
import uk.co.enderfall.sdk.api.registry.BlockSpec;
import uk.co.enderfall.sdk.api.registry.ItemSpec;
import uk.co.enderfall.sdk.api.registry.Rarity;
import uk.co.enderfall.sdk.api.registry.SoundPreset;

/** All blocks and their automatically paired block items are registered together. */
public record DemoBlocks(BlockRef enderAlloy, BlockRef resonanceLamp, BlockRef resonanceWorkbench) {
    public static DemoBlocks register(ModContext context) {
        BlockRef enderAlloy = context.blocks().registerWithItem("ender_alloy_block",
                BlockSpec.builder()
                        .strength(5.0F, 8.0F)
                        .requiresTool()
                        .sound(SoundPreset.METAL)
                        .build(),
                ItemSpec.builder().fireResistant().rarity(Rarity.UNCOMMON).build());
        BlockRef resonanceLamp = context.blocks().registerWithItem("resonance_lamp",
                BlockSpec.builder()
                        .strength(2.0F, 4.0F)
                        .luminance(15)
                        .sound(SoundPreset.GLASS)
                        .build(),
                ItemSpec.builder().rarity(Rarity.UNCOMMON).build());
        BlockRef resonanceWorkbench = context.blocks().registerWithItem("resonance_workbench",
                BlockSpec.builder()
                        .strength(3.5F, 6.0F)
                        .requiresTool()
                        .sound(SoundPreset.METAL)
                        .build(),
                ItemSpec.builder().rarity(Rarity.RARE).build());
        return new DemoBlocks(enderAlloy, resonanceLamp, resonanceWorkbench);
    }
}
