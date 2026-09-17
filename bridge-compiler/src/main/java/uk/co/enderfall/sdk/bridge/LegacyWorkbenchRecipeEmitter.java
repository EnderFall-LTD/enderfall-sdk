package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.RecipeAbi;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Emits legacy container recipes from one positional matching and JSON/wire implementation. */
final class LegacyWorkbenchRecipeEmitter {
    private LegacyWorkbenchRecipeEmitter() { }

    private enum RegistryStyle { DIRECT, LEGACY_HANDLES }

    private record Plan(String canonicalPrefix, String canonicalPackage, String prefix,
                        String outputPackage, RegistryStyle registryStyle) {
        static Plan require(TargetSpec target) throws BridgeGenerationException {
            if (!TargetCatalog.standard().require(target.id()).equals(target)) {
                throw new BridgeGenerationException("Unreviewed legacy recipe target " + target.id());
            }
            if (target.recipeAbi() != RecipeAbi.LEGACY_METHODS) return null;
            boolean fabric = target.loaderAbi() == LoaderAbi.FABRIC;
            return new Plan(fabric ? "Fabric" : "NeoForge",
                    "uk/co/enderfall/sdk/runtime/" + (fabric ? "fabric" : "neoforge") + "/v1_21_4/",
                    fabric ? "Fabric1201" : "LegacyForge",
                    fabric ? "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/" : "uk/co/enderfall/sdk/runtime/forge/v1_20_1/",
                    fabric ? RegistryStyle.DIRECT : RegistryStyle.LEGACY_HANDLES);
        }
        boolean direct() { return registryStyle == RegistryStyle.DIRECT; }
        String recipe() { return prefix + "WorkbenchRecipe"; }
        String binding() { return prefix + "RecipeBinding"; }
        String slotCount() { return direct() ? "inputSlots" : "binding.inputSlots()"; }
    }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> canonicalPaths)
            throws BridgeGenerationException {
        Plan plan = Plan.require(target);
        if (plan == null) return List.of();
        String canonical = plan.canonicalPackage() + plan.canonicalPrefix();
        Set<String> required = Set.of(canonical + "WorkbenchRecipe.java",
                canonical + "RecipeBinding.java", canonical + "WorkbenchBinding.java");
        if (required.stream().noneMatch(canonicalPaths::contains)) return List.of();
        if (!canonicalPaths.containsAll(required)) {
            throw new BridgeGenerationException(target.id() + " requires recipe and both workbench binding declarations");
        }
        return List.of(new RuntimeSource(canonical + "WorkbenchRecipe.java",
                plan.outputPackage() + plan.recipe() + ".java", render(plan).getBytes(StandardCharsets.UTF_8)));
    }

    private static String render(Plan plan) {
        String packageName = plan.outputPackage().substring(0, plan.outputPackage().length() - 1).replace('/', '.');
        String description = plan.direct()
                ? "Minecraft 1.20.1 representation of the portable positional workbench recipe format."
                : "Forge-family 1.20.1 representation of the portable positional workbench recipe.";
        return "package " + packageName + ";\n\n" + IMPORTS + "/** " + description + " */\n"
                + "final class " + plan.recipe() + " implements Recipe<Container> {\n"
                + (plan.direct() ? DIRECT_STATE : HANDLE_STATE).formatted(plan.recipe(), plan.binding())
                + MATCHING
                + (plan.direct() ? DIRECT_ACCESSORS : HANDLE_ACCESSORS)
                + ENTRY
                + (plan.direct() ? DIRECT_SERIALIZER : HANDLE_SERIALIZER).formatted(plan.recipe(), plan.binding())
                + SERIALIZER_METHODS.formatted(plan.recipe(), plan.slotCount(),
                        plan.direct() ? "inputSlots" : "values.size()",
                        plan.direct() ? DIRECT_JSON_VALIDATION : HANDLE_JSON_VALIDATION,
                        plan.direct() ? "type, this" : "binding");
    }

    // Native state/accessor layout stays unchanged for source and bytecode parity.
    // Matching, entry bounds, JSON structure, and network ordering are shared.
    private static final String IMPORTS = """
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
            
            """;

    private static final String DIRECT_STATE = """
                private final ResourceLocation id;
                private final RecipeType<%1$s> type;
                private final RecipeSerializer<%1$s> serializer;
                private final List<Entry> ingredients;
                private final ItemStack result;
            
                private %1$s(ResourceLocation id, RecipeType<%1$s> type,
                                                 RecipeSerializer<%1$s> serializer,
                                                 List<Entry> ingredients, ItemStack result) {
                    this.id = id;
                    this.type = type;
                    this.serializer = serializer;
                    this.ingredients = List.copyOf(ingredients);
                    this.result = result.copy();
                }
            
            """;

    private static final String HANDLE_STATE = """
                private final ResourceLocation id;
                private final %2$s binding;
                private final List<Entry> ingredients;
                private final ItemStack result;
            
                private %1$s(ResourceLocation id, %2$s binding,
                                                   List<Entry> ingredients, ItemStack result) {
                    this.id = Objects.requireNonNull(id, "id");
                    this.binding = Objects.requireNonNull(binding, "binding");
                    this.ingredients = List.copyOf(ingredients);
                    this.result = result.copy();
                }
            
            """;

    private static final String MATCHING = """
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
            
            """;

    private static final String DIRECT_ACCESSORS = """
                @Override
                public ItemStack assemble(Container input, RegistryAccess registries) {
                    return result.copy();
                }
            
                @Override
                public boolean canCraftInDimensions(int width, int height) {
                    return width * height >= ingredients.size();
                }
            
                @Override
                public ItemStack getResultItem(RegistryAccess registries) {
                    return result.copy();
                }
            
                @Override
                public NonNullList<Ingredient> getIngredients() {
                    NonNullList<Ingredient> values = NonNullList.create();
                    ingredients.forEach(entry -> values.add(entry.ingredient()));
                    return values;
                }
            
                @Override
                public ResourceLocation getId() {
                    return id;
                }
            
                @Override
                public RecipeSerializer<?> getSerializer() {
                    return serializer;
                }
            
                @Override
                public RecipeType<?> getType() {
                    return type;
                }
            
                List<Entry> countedIngredients() {
                    return ingredients;
                }
            
                ItemStack result() {
                    return result.copy();
                }
            
            """;

    private static final String HANDLE_ACCESSORS = """
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
            
            """;

    private static final String ENTRY = """
                record Entry(Ingredient ingredient, int count) {
                    Entry {
                        Objects.requireNonNull(ingredient, "ingredient");
                        if (count < 1 || count > 64) {
                            throw new IllegalArgumentException("Ingredient count must be between 1 and 64");
                        }
                    }
                }
            
            """;

    private static final String DIRECT_SERIALIZER = """
                static final class Serializer implements RecipeSerializer<%1$s> {
                    private final RecipeType<%1$s> type;
                    private final int inputSlots;
            
                    Serializer(RecipeType<%1$s> type, int inputSlots) {
                        this.type = type;
                        this.inputSlots = inputSlots;
                    }
            
            """;

    private static final String HANDLE_SERIALIZER = """
                static final class Serializer implements RecipeSerializer<%1$s> {
                    private final %2$s binding;
            
                    Serializer(%2$s binding) {
                        this.binding = binding;
                    }
            
            """;

    private static final String DIRECT_JSON_VALIDATION = """
                            if (count < 1 || count > 64) {
                                throw new JsonParseException("Ingredient count must be between 1 and 64");
                            }
                            ingredients.add(new Entry(Ingredient.fromJson(entry.get("ingredient")), count));
            """;

    private static final String HANDLE_JSON_VALIDATION = """
                            try {
                                ingredients.add(new Entry(Ingredient.fromJson(entry.get("ingredient")), count));
                            } catch (IllegalArgumentException exception) {
                                throw new JsonParseException(exception.getMessage(), exception);
                            }
            """;

    private static final String SERIALIZER_METHODS = """
                    @Override
                    public %1$s fromJson(ResourceLocation id, JsonObject json) {
                        JsonArray values = GsonHelper.getAsJsonArray(json, "ingredients");
                        if (values.size() != %2$s) {
                            throw new JsonParseException("Expected exactly " + %2$s + " positional ingredients");
                        }
                        List<Entry> ingredients = new ArrayList<>(%3$s);
                        for (int index = 0; index < values.size(); index++) {
                            JsonObject entry = GsonHelper.convertToJsonObject(values.get(index), "ingredient");
                            int count = GsonHelper.getAsInt(entry, "count", 1);
            %4$s            }
                        ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
                        return new %1$s(id, %5$s, ingredients, result);
                    }
            
                    @Override
                    public %1$s fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
                        int size = buffer.readVarInt();
                        if (size != %2$s) {
                            throw new IllegalArgumentException("Expected exactly " + %2$s + " positional ingredients");
                        }
                        List<Entry> ingredients = new ArrayList<>(size);
                        for (int index = 0; index < size; index++) {
                            ingredients.add(new Entry(Ingredient.fromNetwork(buffer), buffer.readVarInt()));
                        }
                        return new %1$s(id, %5$s, ingredients, buffer.readItem());
                    }
            
                    @Override
                    public void toNetwork(FriendlyByteBuf buffer, %1$s recipe) {
                        buffer.writeVarInt(recipe.ingredients.size());
                        for (Entry entry : recipe.ingredients) {
                            entry.ingredient().toNetwork(buffer);
                            buffer.writeVarInt(entry.count());
                        }
                        buffer.writeItem(recipe.result);
                    }
                }
            }
            """;

}
