package uk.co.enderfall.sdk.runtime.forge.v1_20_1;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.RegistryObject;
import uk.co.enderfall.sdk.runtime.PortableWorkbenchDefinition;

record LegacyForgeWorkbenchBinding(
        PortableWorkbenchDefinition definition,
        LegacyForgeRecipeBinding recipes,
        RegistryObject<MenuType<LegacyForgeWorkbenchMenu>> menuType) {
}
