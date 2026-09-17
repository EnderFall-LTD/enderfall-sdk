package uk.co.enderfall.sdk.runtime.neoforge.v1_21_4;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Minecraft 1.21.1 replacement for the shared NeoForge workbench recipe. */
final class NeoForgeWorkbenchRecipe implements Recipe<NeoForgeWorkbenchInput> {
    private final NeoForgeRecipeBinding binding;
    private final List<Entry> ingredients;
    private final ItemStack result;

    NeoForgeWorkbenchRecipe(NeoForgeRecipeBinding binding, List<Entry> ingredients, ItemStack result) {
        this.binding = Objects.requireNonNull(binding, "binding");
        this.ingredients = List.copyOf(ingredients);
        this.result = result.copy();
        if (ingredients.size() != binding.inputSlots()) {
            throw new IllegalArgumentException("Expected exactly " + binding.inputSlots() + " ingredients");
        }
    }

    @Override public boolean matches(NeoForgeWorkbenchInput input, Level level) {
        if (input.size() != ingredients.size()) return false;
        for (int slot = 0; slot < ingredients.size(); slot++) {
            Entry expected = ingredients.get(slot);
            ItemStack actual = input.getItem(slot);
            if (!expected.ingredient().test(actual) || actual.getCount() < expected.count()) return false;
        }
        return true;
    }

    @Override public ItemStack assemble(NeoForgeWorkbenchInput input, HolderLookup.Provider registries) {
        return result.copy();
    }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) {
        return width * height >= ingredients.size();
    }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return binding.serializer().get(); }
    @Override public RecipeType<?> getType() { return binding.type().get(); }
    List<Entry> countedIngredients() { return ingredients; }
    ItemStack result() { return result.copy(); }

    static RecipeSerializer<NeoForgeWorkbenchRecipe> serializer(NeoForgeRecipeBinding binding) {
        return new RecipeSerializer<>() {
            @Override public MapCodec<NeoForgeWorkbenchRecipe> codec() { return recipeCodec(binding); }
            @Override public StreamCodec<RegistryFriendlyByteBuf, NeoForgeWorkbenchRecipe> streamCodec() {
                return recipeStreamCodec(binding);
            }
        };
    }

    private static MapCodec<NeoForgeWorkbenchRecipe> recipeCodec(NeoForgeRecipeBinding binding) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Entry.CODEC.codec().listOf().fieldOf("ingredients").forGetter(recipe -> recipe.ingredients),
                ItemStack.CODEC.fieldOf("result").forGetter(recipe -> recipe.result)
        ).apply(instance, (ingredients, result) -> new NeoForgeWorkbenchRecipe(binding, ingredients, result)));
    }

    private static StreamCodec<RegistryFriendlyByteBuf, NeoForgeWorkbenchRecipe> recipeStreamCodec(
            NeoForgeRecipeBinding binding) {
        return new StreamCodec<>() {
            @Override public NeoForgeWorkbenchRecipe decode(RegistryFriendlyByteBuf buffer) {
                int size = buffer.readVarInt();
                if (size != binding.inputSlots()) {
                    throw new IllegalArgumentException("Expected exactly " + binding.inputSlots() + " ingredients");
                }
                List<Entry> ingredients = new ArrayList<>(size);
                for (int index = 0; index < size; index++) ingredients.add(Entry.STREAM_CODEC.decode(buffer));
                return new NeoForgeWorkbenchRecipe(binding, ingredients, ItemStack.STREAM_CODEC.decode(buffer));
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, NeoForgeWorkbenchRecipe recipe) {
                buffer.writeVarInt(recipe.ingredients.size());
                recipe.ingredients.forEach(entry -> Entry.STREAM_CODEC.encode(buffer, entry));
                ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
            }
        };
    }

    record Entry(Ingredient ingredient, int count) {
        Entry {
            Objects.requireNonNull(ingredient, "ingredient");
            if (count < 1 || count > 64) throw new IllegalArgumentException("Ingredient count must be 1-64");
        }
        static final MapCodec<Entry> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Ingredient.CODEC.fieldOf("ingredient").forGetter(Entry::ingredient),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Entry::count)
        ).apply(instance, Entry::new));
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, Entry::ingredient,
                ByteBufCodecs.VAR_INT, Entry::count,
                Entry::new);
    }
}
