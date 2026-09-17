package uk.co.enderfall.sdk.runtime.fabric.v1_21_4;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import uk.co.enderfall.sdk.runtime.PortableWorkbenchDefinition;

record Fabric1201RecipeBinding(RecipeType<Fabric1201WorkbenchRecipe> type,
                               RecipeSerializer<Fabric1201WorkbenchRecipe> serializer, int inputSlots) {
}

record Fabric1201WorkbenchBinding(PortableWorkbenchDefinition definition,
                                  Fabric1201RecipeBinding recipes,
                                  MenuType<Fabric1201WorkbenchMenu> menuType) {
}
