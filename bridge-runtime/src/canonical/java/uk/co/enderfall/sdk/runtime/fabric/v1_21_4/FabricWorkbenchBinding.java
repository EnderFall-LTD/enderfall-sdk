package uk.co.enderfall.sdk.runtime.fabric.v1_21_4;

import net.minecraft.world.inventory.MenuType;
import uk.co.enderfall.sdk.runtime.PortableWorkbenchDefinition;

record FabricWorkbenchBinding(PortableWorkbenchDefinition definition,
                              FabricRecipeBinding recipes,
                              MenuType<FabricWorkbenchMenu> menuType) {
}
