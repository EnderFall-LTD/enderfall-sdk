package uk.co.enderfall.sdk.runtime.neoforge.v1_21_4;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Minecraft 26.2 codec-backed representation of a portable positional workbench recipe. */
final class NeoForge26WorkbenchRecipe implements Recipe<NeoForge26WorkbenchInput> {
    private final NeoForge26RecipeBinding binding;
    private final List<Entry> ingredients;
    private final ItemStackTemplate result;

    NeoForge26WorkbenchRecipe(NeoForge26RecipeBinding binding, List<Entry> ingredients, ItemStackTemplate result) {
        this.binding = binding;
        this.ingredients = List.copyOf(ingredients);
        this.result = result;
        if (ingredients.size() != binding.inputSlots()) {
            throw new IllegalArgumentException("Expected exactly " + binding.inputSlots() + " ingredients");
        }
    }

    @Override
    public boolean matches(NeoForge26WorkbenchInput input, Level level) {
        if (input.size() != ingredients.size()) {
            return false;
        }
        for (int slot = 0; slot < ingredients.size(); slot++) {
            Entry expected = ingredients.get(slot);
            ItemStack actual = input.getItem(slot);
            if (!expected.ingredient().test(actual) || actual.getCount() < expected.count()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(NeoForge26WorkbenchInput input) {
        return result.create();
    }

    @Override public boolean isSpecial() { return true; }
    @Override public boolean showNotification() { return true; }
    @Override public String group() { return binding.id().toString(); }
    @Override public RecipeSerializer<? extends Recipe<NeoForge26WorkbenchInput>> getSerializer() {
        return binding.serializer().get();
    }
    @Override public RecipeType<? extends Recipe<NeoForge26WorkbenchInput>> getType() {
        return binding.type().get();
    }
    @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
    @Override public @Nullable RecipeBookCategory recipeBookCategory() { return null; }

    List<Entry> countedIngredients() {
        return ingredients;
    }

    ItemStack result() {
        return result.create();
    }

    static MapCodec<NeoForge26WorkbenchRecipe> codec(NeoForge26RecipeBinding binding) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Entry.CODEC.codec().listOf().fieldOf("ingredients").forGetter(recipe -> recipe.ingredients),
                ItemStackTemplate.CODEC.fieldOf("result").forGetter(recipe -> recipe.result)
        ).apply(instance, (ingredients, result) -> new NeoForge26WorkbenchRecipe(binding, ingredients, result)));
    }

    static StreamCodec<RegistryFriendlyByteBuf, NeoForge26WorkbenchRecipe> streamCodec(
            NeoForge26RecipeBinding binding) {
        return new StreamCodec<>() {
            @Override
            public NeoForge26WorkbenchRecipe decode(RegistryFriendlyByteBuf buffer) {
                int size = buffer.readVarInt();
                if (size != binding.inputSlots()) {
                    throw new IllegalArgumentException("Expected exactly " + binding.inputSlots() + " ingredients");
                }
                java.util.ArrayList<Entry> ingredients = new java.util.ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    ingredients.add(Entry.STREAM_CODEC.decode(buffer));
                }
                return new NeoForge26WorkbenchRecipe(binding, ingredients, ItemStackTemplate.STREAM_CODEC.decode(buffer));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, NeoForge26WorkbenchRecipe recipe) {
                buffer.writeVarInt(recipe.ingredients.size());
                recipe.ingredients.forEach(entry -> Entry.STREAM_CODEC.encode(buffer, entry));
                ItemStackTemplate.STREAM_CODEC.encode(buffer, recipe.result);
            }
        };
    }

    record Entry(Ingredient ingredient, int count) {
        Entry {
            Objects.requireNonNull(ingredient, "ingredient");
            if (count < 1 || count > 64) {
                throw new IllegalArgumentException("Ingredient count must be between 1 and 64");
            }
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

record NeoForge26WorkbenchInput(List<ItemStack> items) implements RecipeInput {
    NeoForge26WorkbenchInput {
        items = List.copyOf(items);
    }

    @Override public ItemStack getItem(int index) { return items.get(index); }
    @Override public int size() { return items.size(); }
}
