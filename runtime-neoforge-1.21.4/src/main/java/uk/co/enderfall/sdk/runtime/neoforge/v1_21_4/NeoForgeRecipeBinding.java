package uk.co.enderfall.sdk.runtime.neoforge.v1_21_4;

import java.util.function.Supplier;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

record NeoForgeRecipeBinding(
        Supplier<RecipeType<NeoForgeWorkbenchRecipe>> type,
        Supplier<RecipeSerializer<NeoForgeWorkbenchRecipe>> serializer,
        int inputSlots) {
}
