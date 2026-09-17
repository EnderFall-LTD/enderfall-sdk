package uk.co.enderfall.sdk.runtime.forge.v1_20_1;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/** Forge-family 1.20.1 representation of the portable positional workbench recipe. */
final class LegacyForgeWorkbenchRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final LegacyForgeRecipeBinding binding;
    private final List<Entry> ingredients;
    private final ItemStack result;

    private LegacyForgeWorkbenchRecipe(ResourceLocation id, LegacyForgeRecipeBinding binding,
                                       List<Entry> ingredients, ItemStack result) {
        this.id = Objects.requireNonNull(id, "id");
        this.binding = Objects.requireNonNull(binding, "binding");
        this.ingredients = List.copyOf(ingredients);
        this.result = result.copy();
    }

    @Override
    public boolean matches(Container input, Level level) {
        if (input.getContainerSize() < ingredients.size()) {
            return false;
        }
        for (int slot = 0; slot < ingredients.size(); slot++) {
            Entry expected = ingredients.get(slot);
            ItemStack actual = input.getItem(slot);
            if (!expected.ingredient().test(actual) || actual.getCount() < expected.count()) {
                return false;
            }
        }
        for (int slot = ingredients.size(); slot < input.getContainerSize(); slot++) {
            if (!input.getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override public ItemStack assemble(Container input, RegistryAccess registries) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) {
        return width * height >= ingredients.size();
    }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return result.copy(); }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return binding.serializer().get(); }
    @Override public RecipeType<?> getType() { return binding.type().get(); }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> values = NonNullList.create();
        ingredients.forEach(entry -> values.add(entry.ingredient()));
        return values;
    }

    List<Entry> countedIngredients() { return ingredients; }
    ItemStack result() { return result.copy(); }

    record Entry(Ingredient ingredient, int count) {
        Entry {
            Objects.requireNonNull(ingredient, "ingredient");
            if (count < 1 || count > 64) {
                throw new IllegalArgumentException("Ingredient count must be between 1 and 64");
            }
        }
    }

    static final class Serializer implements RecipeSerializer<LegacyForgeWorkbenchRecipe> {
        private final LegacyForgeRecipeBinding binding;

        Serializer(LegacyForgeRecipeBinding binding) {
            this.binding = binding;
        }

        @Override
        public LegacyForgeWorkbenchRecipe fromJson(ResourceLocation id, JsonObject json) {
            JsonArray values = GsonHelper.getAsJsonArray(json, "ingredients");
            if (values.size() != binding.inputSlots()) {
                throw new JsonParseException("Expected exactly " + binding.inputSlots() + " positional ingredients");
            }
            List<Entry> ingredients = new ArrayList<>(values.size());
            for (int index = 0; index < values.size(); index++) {
                JsonObject entry = GsonHelper.convertToJsonObject(values.get(index), "ingredient");
                int count = GsonHelper.getAsInt(entry, "count", 1);
                try {
                    ingredients.add(new Entry(Ingredient.fromJson(entry.get("ingredient")), count));
                } catch (IllegalArgumentException exception) {
                    throw new JsonParseException(exception.getMessage(), exception);
                }
            }
            ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
            return new LegacyForgeWorkbenchRecipe(id, binding, ingredients, result);
        }

        @Override
        public LegacyForgeWorkbenchRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            if (size != binding.inputSlots()) {
                throw new IllegalArgumentException("Expected exactly " + binding.inputSlots() + " positional ingredients");
            }
            List<Entry> ingredients = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                ingredients.add(new Entry(Ingredient.fromNetwork(buffer), buffer.readVarInt()));
            }
            return new LegacyForgeWorkbenchRecipe(id, binding, ingredients, buffer.readItem());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, LegacyForgeWorkbenchRecipe recipe) {
            buffer.writeVarInt(recipe.ingredients.size());
            for (Entry entry : recipe.ingredients) {
                entry.ingredient().toNetwork(buffer);
                buffer.writeVarInt(entry.count());
            }
            buffer.writeItem(recipe.result);
        }
    }
}
