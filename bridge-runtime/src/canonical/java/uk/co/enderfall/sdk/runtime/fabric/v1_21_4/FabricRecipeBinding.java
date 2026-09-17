package uk.co.enderfall.sdk.runtime.fabric.v1_21_4;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

record FabricRecipeBinding(RecipeType<FabricWorkbenchRecipe> type,
                           RecipeSerializer<FabricWorkbenchRecipe> serializer,
                           int inputSlots) {
}
