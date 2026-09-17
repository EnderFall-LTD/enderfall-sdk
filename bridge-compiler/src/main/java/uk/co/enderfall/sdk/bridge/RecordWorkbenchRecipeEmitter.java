package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.RecipeAbi;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Shared 26.2 template-result recipe emission, preserving native registration timing. */
final class RecordWorkbenchRecipeEmitter {
    private RecordWorkbenchRecipeEmitter() { }

    private record Plan(boolean fabric) {
        String canonicalPrefix() { return fabric ? "Fabric" : "NeoForge"; }
        String prefix() { return fabric ? "Fabric" : "NeoForge26"; }
        String root() { return "uk/co/enderfall/sdk/runtime/" + (fabric ? "fabric" : "neoforge") + "/v1_21_4/"; }
        String recipe() { return prefix() + "WorkbenchRecipe"; }
        String binding() { return prefix() + "RecipeBinding"; }
        String input() { return prefix() + "WorkbenchInput"; }
        String parameter() { return fabric ? "java.util.function.Supplier<" + binding() + ">" : binding(); }
        String handle() { return fabric ? "" : ".get()"; }
        String resolved() { return fabric ? "resolved" : "binding"; }
        String gap() { return fabric ? "" : "\n"; }
    }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> paths) throws BridgeGenerationException {
        if (!TargetCatalog.standard().require(target.id()).equals(target)) {
            throw new BridgeGenerationException("Unreviewed record recipe target " + target.id());
        }
        if (target.recipeAbi() != RecipeAbi.SERIALIZER_RECORD_26) return List.of();
        Plan plan = new Plan(target.loaderAbi() == LoaderAbi.FABRIC);
        String canonical = plan.root() + plan.canonicalPrefix();
        Set<String> required = Set.of(canonical + "WorkbenchRecipe.java", canonical + "RecipeBinding.java",
                canonical + "WorkbenchInput.java");
        if (required.stream().noneMatch(paths::contains)) return List.of();
        if (!paths.containsAll(required)) throw new BridgeGenerationException(
                target.id() + " requires recipe, binding, and recipe-input declarations");
        String filename = plan.fabric() ? "Fabric26WorkbenchRecipe.java" : plan.recipe() + ".java";
        return List.of(new RuntimeSource(canonical + "WorkbenchRecipe.java", plan.root() + filename,
                render(plan).getBytes(StandardCharsets.UTF_8)));
    }

    private static String render(Plan p) {
        StringBuilder out = new StringBuilder(header(p));
        out.append("""
                final class %s implements Recipe<%s> {
                    private final %s binding;
                    private final List<Entry> ingredients;
                    private final ItemStackTemplate result;

                    %s(%s binding, List<Entry> ingredients, ItemStackTemplate result) {
                        this.binding = %s;
                        this.ingredients = List.copyOf(ingredients);
                        this.result = result;
                        if (ingredients.size() != binding.inputSlots()) {
                            throw new IllegalArgumentException("Expected exactly " + binding.inputSlots() + " ingredients");
                        }
                    }
                """.formatted(p.recipe(), p.input(), p.binding(), p.recipe(), p.binding(),
                        p.fabric() ? "Objects.requireNonNull(binding, \"binding\")" : "binding"));
        out.append(p.gap()).append(p.fabric() ? "    @Override " : "    @Override\n    ")
                .append("public boolean matches(" + p.input() + " input, Level level) {\n");
        guard(out, p, 8, "input.size() != ingredients.size()", "return false;");
        out.append("""
                        for (int slot = 0; slot < ingredients.size(); slot++) {
                            Entry expected = ingredients.get(slot);
                            ItemStack actual = input.getItem(slot);
                """);
        guard(out, p, 12, "!expected.ingredient().test(actual) || actual.getCount() < expected.count()", "return false;");
        out.append("        }\n        return true;\n    }\n").append(p.gap());
        if (p.fabric()) out.append("    @Override public ItemStack assemble(" + p.input() + " input) { return result.create(); }\n");
        else out.append("    @Override\n    public ItemStack assemble(" + p.input() + " input) {\n        return result.create();\n    }\n\n");
        out.append("""
                    @Override public boolean isSpecial() { return true; }
                    @Override public boolean showNotification() { return true; }
                    @Override public String group() { return binding.%s().toString(); }
                    @Override public RecipeSerializer<? extends Recipe<%s>> getSerializer() {
                        return binding.serializer()%s;
                    }
                """.formatted(p.fabric() ? "type" : "id", p.input(), p.handle()));
        out.append("    @Override public RecipeType<? extends Recipe<" + p.input() + ">> getType() {");
        out.append(p.fabric() ? " return binding.type(); }\n" : "\n        return binding.type().get();\n    }\n");
        out.append("""
                    @Override public PlacementInfo placementInfo() { return PlacementInfo.NOT_PLACEABLE; }
                    @Override public @Nullable RecipeBookCategory recipeBookCategory() { return null; }
                """).append(p.gap());
        if (p.fabric()) out.append("    List<Entry> countedIngredients() { return ingredients; }\n    ItemStack result() { return result.create(); }\n\n");
        else out.append("    List<Entry> countedIngredients() {\n        return ingredients;\n    }\n\n    ItemStack result() {\n        return result.create();\n    }\n\n");
        if (p.fabric()) out.append("""
                    static RecipeSerializer<%s> serializer(%s binding) {
                        return new RecipeSerializer<>(recipeCodec(binding), recipeStreamCodec(binding));
                    }
                """.formatted(p.recipe(), p.parameter()));
        out.append("""
                    %sMapCodec<%s> %s(%s binding) {
                        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                                Entry.CODEC.codec().listOf().fieldOf("ingredients").forGetter(recipe -> recipe.ingredients),
                                ItemStackTemplate.CODEC.fieldOf("result").forGetter(recipe -> recipe.result)
                        ).apply(instance, (ingredients, result) -> new %s(%s, ingredients, result)));
                    }
                """.formatted(p.fabric() ? "private static " : "static ", p.recipe(),
                        p.fabric() ? "recipeCodec" : "codec", p.parameter(), p.recipe(), p.fabric() ? "binding.get()" : "binding"));
        appendStream(out, p);
        out.append(p.gap()).append("    record Entry(Ingredient ingredient, int count) {\n        Entry {\n            Objects.requireNonNull(ingredient, \"ingredient\");\n");
        guard(out, p, 12, "count < 1 || count > 64", "throw new IllegalArgumentException(\"Ingredient count must be "
                + (p.fabric() ? "1-64" : "between 1 and 64") + "\");");
        out.append("""
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
        if (!p.fabric()) out.append("""

                record %s(List<ItemStack> items) implements RecipeInput {
                    %s {
                        items = List.copyOf(items);
                    }

                    @Override public ItemStack getItem(int index) { return items.get(index); }
                    @Override public int size() { return items.size(); }
                }
                """.formatted(p.input(), p.input()));
        return out.toString();
    }

    private static void appendStream(StringBuilder out, Plan p) {
        out.append(p.gap()).append("    " + (p.fabric() ? "private static " : "static "))
                .append("StreamCodec<RegistryFriendlyByteBuf, " + p.recipe() + "> " + (p.fabric() ? "recipeStreamCodec" : "streamCodec"))
                .append("(\n            " + p.parameter() + " binding) {\n        return new StreamCodec<>() {\n")
                .append(p.fabric() ? "            @Override " : "            @Override\n            ")
                .append("public " + p.recipe() + " decode(RegistryFriendlyByteBuf buffer) {\n");
        if (p.fabric()) out.append("                " + p.binding() + " resolved = binding.get();\n");
        out.append("                int size = buffer.readVarInt();\n");
        if (p.fabric()) out.append("                if (size != resolved.inputSlots()) throw new IllegalArgumentException(\n                        \"Expected exactly \" + resolved.inputSlots() + \" ingredients\");\n");
        else guard(out, p, 16, "size != binding.inputSlots()", "throw new IllegalArgumentException(\"Expected exactly \" + binding.inputSlots() + \" ingredients\");");
        out.append(p.fabric() ? "                List<Entry> ingredients = new ArrayList<>(size);\n"
                : "                java.util.ArrayList<Entry> ingredients = new java.util.ArrayList<>(size);\n");
        out.append("                for (int index = 0; index < size; index++)");
        out.append(p.fabric() ? " ingredients.add(Entry.STREAM_CODEC.decode(buffer));\n"
                : " {\n                    ingredients.add(Entry.STREAM_CODEC.decode(buffer));\n                }\n");
        out.append("                return new " + p.recipe() + "(" + p.resolved() + ", ingredients, ItemStackTemplate.STREAM_CODEC.decode(buffer));\n            }\n")
                .append(p.gap()).append(p.fabric() ? "            @Override " : "            @Override\n            ")
                .append("public void encode(RegistryFriendlyByteBuf buffer, " + p.recipe() + " recipe) {\n")
                .append("""
                                buffer.writeVarInt(recipe.ingredients.size());
                                recipe.ingredients.forEach(entry -> Entry.STREAM_CODEC.encode(buffer, entry));
                                ItemStackTemplate.STREAM_CODEC.encode(buffer, recipe.result);
                            }
                        };
                    }
                """);
    }

    // Formatting is deliberately retained to keep the independent source/class goldens stable.
    private static void guard(StringBuilder out, Plan p, int indent, String condition, String statement) {
        String pad = " ".repeat(indent);
        out.append(pad).append("if (").append(condition).append(')');
        if (p.fabric()) out.append(' ').append(statement).append('\n');
        else out.append(" {\n").append(pad).append("    ").append(statement).append('\n').append(pad).append("}\n");
    }

    private static String header(Plan p) {
        var imports = new TreeSet<>(List.of("com.mojang.serialization.Codec", "com.mojang.serialization.MapCodec",
                "com.mojang.serialization.codecs.RecordCodecBuilder", "java.util.List", "java.util.Objects",
                "net.minecraft.network.RegistryFriendlyByteBuf", "net.minecraft.network.codec.ByteBufCodecs",
                "net.minecraft.network.codec.StreamCodec", "net.minecraft.world.item.ItemStack",
                "net.minecraft.world.item.ItemStackTemplate", "net.minecraft.world.item.crafting.Ingredient",
                "net.minecraft.world.item.crafting.PlacementInfo", "net.minecraft.world.item.crafting.Recipe",
                "net.minecraft.world.item.crafting.RecipeBookCategory", "net.minecraft.world.item.crafting.RecipeSerializer",
                "net.minecraft.world.item.crafting.RecipeType", "net.minecraft.world.level.Level", "org.jspecify.annotations.Nullable"));
        if (p.fabric()) imports.add("java.util.ArrayList");
        else { imports.add("java.util.function.Supplier"); imports.add("net.minecraft.world.item.crafting.RecipeInput"); }
        StringBuilder out = new StringBuilder("package " + p.root().substring(0, p.root().length() - 1).replace('/', '.') + ";\n\n");
        imports.forEach(name -> out.append("import " + name + ";\n"));
        return out.append("\n/** Minecraft 26.2 ").append(p.fabric() ? "replacement for Fabric's portable positional recipe."
                : "codec-backed representation of a portable positional workbench recipe.").append(" */\n").toString();
    }
}
