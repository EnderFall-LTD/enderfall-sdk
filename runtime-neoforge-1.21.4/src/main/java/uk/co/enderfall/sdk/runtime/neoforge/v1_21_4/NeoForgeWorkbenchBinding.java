package uk.co.enderfall.sdk.runtime.neoforge.v1_21_4;

import java.util.function.Supplier;
import net.minecraft.world.inventory.MenuType;
import uk.co.enderfall.sdk.runtime.PortableWorkbenchDefinition;

record NeoForgeWorkbenchBinding(
        PortableWorkbenchDefinition definition,
        NeoForgeRecipeBinding recipes,
        Supplier<MenuType<NeoForgeWorkbenchMenu>> menuType) {
}
