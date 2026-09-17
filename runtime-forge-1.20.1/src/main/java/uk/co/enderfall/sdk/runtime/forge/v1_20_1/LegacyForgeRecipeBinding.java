package uk.co.enderfall.sdk.runtime.forge.v1_20_1;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.RegistryObject;

record LegacyForgeRecipeBinding(
        RegistryObject<RecipeType<LegacyForgeWorkbenchRecipe>> type,
        RegistryObject<RecipeSerializer<LegacyForgeWorkbenchRecipe>> serializer,
        int inputSlots) {
}
