package uk.co.enderfall.sdk.demo.content;

import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.registry.ItemRef;
import uk.co.enderfall.sdk.api.registry.ItemSpec;
import uk.co.enderfall.sdk.api.registry.Rarity;

/** All standalone item registration lives here. */
public record DemoItems(ItemRef voidCrystal, ItemRef resonanceRod, ItemRef resonanceCore) {
    public static DemoItems register(ModContext context) {
        ItemRef voidCrystal = context.items().register("void_crystal", ItemSpec.builder()
                .maxStackSize(16)
                .fireResistant()
                .rarity(Rarity.RARE)
                .build());
        ItemRef resonanceRod = context.items().register("resonance_rod", ItemSpec.builder()
                .maxStackSize(1)
                .durability(384)
                .rarity(Rarity.UNCOMMON)
                .build());
        ItemRef resonanceCore = context.items().register("resonance_core", ItemSpec.builder()
                .maxStackSize(16)
                .fireResistant()
                .rarity(Rarity.EPIC)
                .build());
        return new DemoItems(voidCrystal, resonanceRod, resonanceCore);
    }
}
