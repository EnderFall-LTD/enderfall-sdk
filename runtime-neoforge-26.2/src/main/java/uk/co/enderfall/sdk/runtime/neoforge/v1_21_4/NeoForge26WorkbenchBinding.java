package uk.co.enderfall.sdk.runtime.neoforge.v1_21_4;

import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import uk.co.enderfall.sdk.runtime.PortableWorkbenchDefinition;

record NeoForge26RecipeBinding(Identifier id,
                               Supplier<RecipeType<NeoForge26WorkbenchRecipe>> type,
                               Supplier<RecipeSerializer<NeoForge26WorkbenchRecipe>> serializer,
                               int inputSlots) {
}

record NeoForge26WorkbenchBinding(PortableWorkbenchDefinition definition,
                                  NeoForge26RecipeBinding recipes,
                                  Supplier<MenuType<NeoForge26WorkbenchMenu>> menuType) {
}
