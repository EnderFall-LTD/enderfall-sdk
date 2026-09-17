package uk.co.enderfall.sdk.runtime.neoforge.v1_21_4;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

record NeoForgeWorkbenchInput(List<ItemStack> items) implements RecipeInput {
    NeoForgeWorkbenchInput { items = List.copyOf(items); }
    @Override public ItemStack getItem(int index) { return items.get(index); }
    @Override public int size() { return items.size(); }
}
