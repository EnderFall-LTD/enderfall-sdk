package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.RecipeAbi;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Shared positional recipe/serializer emission for the two reviewed 1.21 codec ABIs. */
final class CodecWorkbenchRecipeEmitter {
    private CodecWorkbenchRecipeEmitter() { }

    private record Plan(String prefix, String packagePath, boolean placement, boolean fabric) {
        static Plan require(TargetSpec target) throws BridgeGenerationException {
            if (!TargetCatalog.standard().require(target.id()).equals(target)) {
                throw new BridgeGenerationException("Unreviewed recipe target " + target.id());
            }
            boolean placement = target.recipeAbi() == RecipeAbi.CODEC_WITH_PLACEMENT_1_21_4;
            if (!placement && target.recipeAbi() != RecipeAbi.CODEC_1_21_1) return null;
            boolean fabric = target.loaderAbi() == LoaderAbi.FABRIC;
            return new Plan(fabric ? "Fabric" : "NeoForge",
                    "uk/co/enderfall/sdk/runtime/" + (fabric ? "fabric" : "neoforge") + "/v1_21_4/",
                    placement, fabric);
        }
        String recipe() { return prefix + "WorkbenchRecipe"; }
        String binding() { return prefix + "RecipeBinding"; }
        String input() { return prefix + "WorkbenchInput"; }
        String handleGet() { return fabric ? "" : ".get()"; }
        boolean compact() { return fabric && !placement; }
        String sectionGap() { return compact() ? "" : "\n"; }
    }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> canonicalPaths)
            throws BridgeGenerationException {
        Plan plan = Plan.require(target);
        if (plan == null) return List.of(); // Unmigrated ABIs retain their existing emitters.
        Set<String> required = Set.of(plan.packagePath() + plan.recipe() + ".java",
                plan.packagePath() + plan.binding() + ".java", plan.packagePath() + plan.input() + ".java");
        if (required.stream().noneMatch(canonicalPaths::contains)) return List.of();
        if (!canonicalPaths.containsAll(required)) {
            throw new BridgeGenerationException(target.id() + " requires recipe, binding, and recipe-input declarations");
        }
        String output = plan.prefix() + (plan.placement() ? "" : "1211") + "WorkbenchRecipe.java";
        return List.of(new RuntimeSource(plan.packagePath() + plan.recipe() + ".java",
                plan.packagePath() + output, render(plan).getBytes(StandardCharsets.UTF_8)));
    }

    private static String render(Plan plan) {
        StringBuilder out = new StringBuilder(header(plan));
        out.append("""
                final class %s implements Recipe<%s> {
                    private final %s binding;
                    private final List<Entry> ingredients;
                    private final ItemStack result;

                    %s(%s binding, List<Entry> ingredients, ItemStack result) {
                        this.binding = Objects.requireNonNull(binding, "binding");
                        this.ingredients = List.copyOf(ingredients);
                        this.result = result.copy();
                        if (ingredients.size() != binding.inputSlots()) {
                            throw new IllegalArgumentException("Expected exactly " + binding.inputSlots() + " ingredients");
                        }
                    }
                """.formatted(plan.recipe(), plan.input(), plan.binding(), plan.recipe(), plan.binding()));
        out.append(plan.sectionGap());
        out.append(plan.placement() && !plan.fabric() ? "    @Override\n    " : "    @Override ");
        out.append("""
                public boolean matches(%s input, Level level) {
                        if (input.size() != ingredients.size()) return false;
                        for (int slot = 0; slot < ingredients.size(); slot++) {
                            Entry expected = ingredients.get(slot);
                            ItemStack actual = input.getItem(slot);
                            if (!expected.ingredient().test(actual) || actual.getCount() < expected.count()) return false;
                        }
                        return true;
                    }
                """.formatted(plan.input()));
        if (!plan.fabric()) out.append('\n');
        out.append("""
                    @Override public ItemStack assemble(%s input, HolderLookup.Provider registries) {
                        return result.copy();
                    }
                """.formatted(plan.input()));
        appendRecipeInterface(out, plan);
        out.append("""
                    List<Entry> countedIngredients() { return ingredients; }
                    ItemStack result() { return result.copy(); }

                """);
        appendSerializer(out, plan);
        out.append(plan.sectionGap()).append("""
                    private static MapCodec<%s> recipeCodec(%s binding) {
                        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                                Entry.CODEC.codec().listOf().fieldOf("ingredients").forGetter(recipe -> recipe.ingredients),
                                ItemStack.CODEC.fieldOf("result").forGetter(recipe -> recipe.result)
                        ).apply(instance, (ingredients, result) -> new %s(binding, ingredients, result)));
                    }
                """.formatted(plan.recipe(), plan.binding(), plan.recipe()));
        appendStreamCodec(out, plan);
        out.append(plan.sectionGap()).append("""
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
                """);
        return out.toString();
    }

    private static void appendRecipeInterface(StringBuilder out, Plan plan) {
        if (!plan.placement()) {
            out.append("""
                        @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return result.copy(); }
                        @Override public boolean canCraftInDimensions(int width, int height) {
                            return width * height >= ingredients.size();
                        }
                        @Override public boolean isSpecial() { return true; }
                        @Override public RecipeSerializer<?> getSerializer() { return binding.serializer()%s; }
                        @Override public RecipeType<?> getType() { return binding.type()%s; }
                    """.formatted(plan.handleGet(), plan.handleGet()));
        } else {
            out.append("""
                        @Override public boolean isSpecial() { return true; }
                        @Override public RecipeSerializer<? extends Recipe<%s>> getSerializer() {
                            return binding.serializer()%s;
                        }
                        @Override public RecipeType<? extends Recipe<%s>> getType() { return binding.type()%s; }
                        @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
                        @Override public RecipeBookCategory recipeBookCategory() { return null; }
                    """.formatted(plan.input(), plan.handleGet(), plan.input(), plan.handleGet()));
            if (!plan.fabric()) out.append('\n');
        }
    }

    private static void appendSerializer(StringBuilder out, Plan plan) {
        String parameter = plan.fabric() ? "java.util.function.Supplier<" + plan.binding() + ">" : plan.binding();
        String binding = plan.fabric() ? "binding.get()" : "binding";
        out.append("""
                    static RecipeSerializer<%s> serializer(%s binding) {
                        return new RecipeSerializer<>() {
                            @Override public MapCodec<%s> codec() { return recipeCodec(%s); }
                """.formatted(plan.recipe(), parameter, plan.recipe(), binding));
        if (!plan.placement()) out.append("            @Override ");
        else out.append(plan.fabric() ? "            @Override @SuppressWarnings(\"deprecation\")\n            "
                : "            @Override\n            @SuppressWarnings(\"deprecation\")\n            ");
        out.append("""
                public StreamCodec<RegistryFriendlyByteBuf, %s> streamCodec() {
                                return recipeStreamCodec(%s);
                            }
                        };
                    }
                """.formatted(plan.recipe(), binding));
    }

    private static void appendStreamCodec(StringBuilder out, Plan plan) {
        out.append(plan.sectionGap()).append("""
                    private static StreamCodec<RegistryFriendlyByteBuf, %s> recipeStreamCodec(
                            %s binding) {
                        return new StreamCodec<>() {
                            @Override public %s decode(RegistryFriendlyByteBuf buffer) {
                                int size = buffer.readVarInt();
                """.formatted(plan.recipe(), plan.binding(), plan.recipe()));
        if (plan.compact()) {
            out.append("""
                                    if (size != binding.inputSlots()) throw new IllegalArgumentException(
                                            "Expected exactly " + binding.inputSlots() + " ingredients");
                    """);
        } else {
            out.append("""
                                    if (size != binding.inputSlots()) {
                                        throw new IllegalArgumentException("Expected exactly " + binding.inputSlots() + " ingredients");
                                    }
                    """);
        }
        out.append("""
                                List<Entry> ingredients = new ArrayList<>(size);
                                for (int index = 0; index < size; index++) ingredients.add(Entry.STREAM_CODEC.decode(buffer));
                                return new %s(binding, ingredients, ItemStack.STREAM_CODEC.decode(buffer));
                            }
                            @Override public void encode(RegistryFriendlyByteBuf buffer, %s recipe) {
                                buffer.writeVarInt(recipe.ingredients.size());
                                recipe.ingredients.forEach(entry -> Entry.STREAM_CODEC.encode(buffer, entry));
                                ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
                            }
                        };
                    }
                """.formatted(plan.recipe(), plan.recipe()));
    }

    private static String header(Plan plan) {
        var imports = new TreeSet<>(List.of("com.mojang.serialization.Codec", "com.mojang.serialization.MapCodec",
                "com.mojang.serialization.codecs.RecordCodecBuilder", "java.util.ArrayList", "java.util.List",
                "java.util.Objects", "net.minecraft.core.HolderLookup", "net.minecraft.network.RegistryFriendlyByteBuf",
                "net.minecraft.network.codec.ByteBufCodecs", "net.minecraft.network.codec.StreamCodec",
                "net.minecraft.world.item.ItemStack", "net.minecraft.world.item.crafting.Ingredient",
                "net.minecraft.world.item.crafting.Recipe", "net.minecraft.world.item.crafting.RecipeSerializer",
                "net.minecraft.world.item.crafting.RecipeType", "net.minecraft.world.level.Level"));
        if (plan.placement()) {
            imports.add("net.minecraft.world.item.crafting.PlacementInfo");
            imports.add("net.minecraft.world.item.crafting.RecipeBookCategory");
            if (!plan.fabric()) imports.add("net.minecraft.world.item.crafting.RecipeInput");
        }
        var out = new StringBuilder("package ")
                .append(plan.packagePath().substring(0, plan.packagePath().length() - 1).replace('/', '.')).append(";\n\n");
        imports.forEach(name -> out.append("import ").append(name).append(";\n"));
        String description = plan.placement() ? plan.prefix() + " 1.21.4 codec-backed portable positional recipe."
                : plan.fabric() ? "Minecraft 1.21.1 replacement for Fabric's portable positional recipe."
                : "Minecraft 1.21.1 replacement for the shared NeoForge workbench recipe.";
        return out.append("\n/** ").append(description).append(" */\n").toString();
    }
}
