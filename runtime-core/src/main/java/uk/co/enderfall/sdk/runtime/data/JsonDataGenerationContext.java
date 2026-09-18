package uk.co.enderfall.sdk.runtime.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.data.DataGenerationContext;
import uk.co.enderfall.sdk.api.data.Ingredient;
import uk.co.enderfall.sdk.api.data.ModelSpec;
import uk.co.enderfall.sdk.api.data.ShapedRecipeSpec;
import uk.co.enderfall.sdk.api.data.ShapelessRecipeSpec;
import uk.co.enderfall.sdk.api.data.TagSpec;
import uk.co.enderfall.sdk.api.platform.MinecraftVersion;
import uk.co.enderfall.sdk.api.recipe.CountedIngredient;
import uk.co.enderfall.sdk.api.recipe.WorkbenchRecipeSpec;

/** Deterministic build-time resource collector. Game-load tests validate each target format. */
public final class JsonDataGenerationContext implements DataGenerationContext {
    private static final MinecraftVersion SINGULAR_DATA_PATHS = new MinecraftVersion("1.21.0");
    private static final MinecraftVersion HOLDER_SET_INGREDIENTS = new MinecraftVersion("1.21.2");
    private static final MinecraftVersion ITEM_DEFINITIONS = new MinecraftVersion("1.21.4");

    private final MinecraftVersion minecraftVersion;
    private final String modNamespace;
    private final Map<String, String> resources = new TreeMap<>();
    private final Map<String, Map<String, String>> translations = new TreeMap<>();

    public JsonDataGenerationContext(MinecraftVersion minecraftVersion) {
        this(minecraftVersion, "enderfall-generated");
    }

    public JsonDataGenerationContext(MinecraftVersion minecraftVersion, String modNamespace) {
        this.minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        this.modNamespace = new ResourceId(Objects.requireNonNull(modNamespace, "modNamespace"), "validation")
                .namespace();
    }

    @Override
    public void shapedRecipe(ResourceId id, ShapedRecipeSpec recipe) {
        StringBuilder json = new StringBuilder("{\n  \"type\": \"minecraft:crafting_shaped\",\n  \"pattern\": [");
        for (int index = 0; index < recipe.pattern().size(); index++) {
            if (index > 0) {
                json.append(", ");
            }
            json.append(quote(recipe.pattern().get(index)));
        }
        json.append("],\n  \"key\": {");
        int keyIndex = 0;
        for (Map.Entry<Character, Ingredient> entry : new TreeMap<>(recipe.keys()).entrySet()) {
            if (keyIndex++ > 0) {
                json.append(',');
            }
            json.append("\n    ").append(quote(entry.getKey().toString())).append(": ")
                    .append(ingredient(entry.getValue()));
        }
        json.append("\n  },\n  \"result\": ").append(result(recipe.result().item(), recipe.result().count()))
                .append("\n}\n");
        put(dataPath(id, dataDirectory("recipe")), json.toString());
    }

    @Override
    public void shapelessRecipe(ResourceId id, ShapelessRecipeSpec recipe) {
        StringBuilder json = new StringBuilder("{\n  \"type\": \"minecraft:crafting_shapeless\",\n")
                .append("  \"ingredients\": [");
        for (int index = 0; index < recipe.ingredients().size(); index++) {
            if (index > 0) {
                json.append(", ");
            }
            json.append(ingredient(recipe.ingredients().get(index)));
        }
        json.append("],\n  \"result\": ").append(result(recipe.result().item(), recipe.result().count()))
                .append("\n}\n");
        put(dataPath(id, dataDirectory("recipe")), json.toString());
    }

    @Override
    public void workbenchRecipe(ResourceId id, WorkbenchRecipeSpec recipe) {
        StringBuilder json = new StringBuilder("{\n  \"type\": ")
                .append(quote(recipe.type().id().toString()))
                .append(",\n  \"ingredients\": [");
        for (int index = 0; index < recipe.ingredients().size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            CountedIngredient counted = recipe.ingredients().get(index);
            json.append("\n    { \"ingredient\": ")
                    .append(ingredient(counted.ingredient()))
                    .append(", \"count\": ").append(counted.count()).append(" }");
        }
        String resultKey = minecraftVersion.compareTo(SINGULAR_DATA_PATHS) >= 0 ? "id" : "item";
        json.append("\n  ],\n  \"result\": { \"").append(resultKey).append("\": ")
                .append(quote(recipe.result().item().toString()))
                .append(", \"count\": ").append(recipe.result().count()).append(" }\n}\n");
        put(dataPath(id, dataDirectory("recipe")), json.toString());
    }

    @Override
    public void itemModel(ResourceId item, ModelSpec model) {
        requireKind(model, ModelSpec.Kind.GENERATED_ITEM, ModelSpec.Kind.HANDHELD_ITEM);
        String parent = model.kind() == ModelSpec.Kind.HANDHELD_ITEM ? "item/handheld" : "item/generated";
        String modelPath = assetPath(item, "models/item");
        put(modelPath, "{\n  \"parent\": \"minecraft:" + parent + "\",\n  \"textures\": {\n"
                + "    \"layer0\": " + quote(model.texture().toString()) + "\n  }\n}\n");
        if (minecraftVersion.compareTo(ITEM_DEFINITIONS) >= 0) {
            put(assetPath(item, "items"), "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n"
                    + "    \"model\": " + quote(item.namespace() + ":item/" + item.path()) + "\n  }\n}\n");
        }
    }

    @Override
    public void blockModel(ResourceId block, ModelSpec model) {
        requireKind(model, ModelSpec.Kind.CUBE_ALL_BLOCK);
        put(assetPath(block, "models/block"), "{\n  \"parent\": \"minecraft:block/cube_all\",\n"
                + "  \"textures\": {\n    \"all\": " + quote(model.texture().toString()) + "\n  }\n}\n");
        put(assetPath(block, "blockstates"), "{\n  \"variants\": {\n    \"\": { \"model\": "
                + quote(block.namespace() + ":block/" + block.path()) + " }\n  }\n}\n");
        put(assetPath(block, "models/item"), "{\n  \"parent\": "
                + quote(block.namespace() + ":block/" + block.path()) + "\n}\n");
        if (minecraftVersion.compareTo(ITEM_DEFINITIONS) >= 0) {
            put(assetPath(block, "items"), "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n"
                    + "    \"model\": " + quote(block.namespace() + ":item/" + block.path()) + "\n  }\n}\n");
        }
    }

    @Override
    public void horizontalBlockState(ResourceId block, ResourceId model) {
        facingBlockState(block, model, false);
    }

    @Override
    public void axisBlockState(ResourceId block, ResourceId model) {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(model, "model");
        put(assetPath(block, "blockstates"), "{\n  \"variants\": {\n"
                + "    \"axis=x\": { \"model\": " + quote(model.toString()) + ", \"x\": 90, \"y\": 90 },\n"
                + "    \"axis=y\": { \"model\": " + quote(model.toString()) + " },\n"
                + "    \"axis=z\": { \"model\": " + quote(model.toString()) + ", \"x\": 90 }\n"
                + "  }\n}\n");
    }

    @Override
    public void blockStates(ResourceId block,
            uk.co.enderfall.sdk.api.block.BlockStateDefinition definition,
            java.util.function.Function<uk.co.enderfall.sdk.api.block.PortableBlockState, ResourceId> model) {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(model, "model");
        StringBuilder json = new StringBuilder("{\n  \"variants\": {\n");
        boolean first = true;
        for (var state : definition.states()) {
            if (!first) json.append(",\n");
            first = false;
            String key = state.serializedValues().entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(java.util.stream.Collectors.joining(","));
            var selected = Objects.requireNonNull(model.apply(state), "model returned null");
            json.append("    ").append(quote(key)).append(": { \"model\": ")
                    .append(quote(selected.toString())).append(" }");
        }
        put(assetPath(block, "blockstates"), json.append("\n  }\n}\n").toString());
    }

    @Override
    public void sixWayBlockState(ResourceId block, ResourceId model) {
        facingBlockState(block, model, true);
    }

    private void facingBlockState(ResourceId block, ResourceId model, boolean sixWay) {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(model, "model");
        String[] directions = sixWay ? new String[] { "north", "east", "south", "west", "up", "down" }
                : new String[] { "north", "east", "south", "west" };
        StringBuilder json = new StringBuilder("{\n  \"variants\": {\n");
        for (int i = 0; i < directions.length; i++) {
            if (i != 0) json.append(",\n");
            json.append("    ").append(quote("facing=" + directions[i])).append(": { \"model\": ")
                    .append(quote(model.toString())).append(", \"y\": ").append(i < 4 ? i * 90 : 0);
            if (i >= 4) json.append(", \"x\": ").append(i == 4 ? 270 : 90);
            json.append(", \"uvlock\": true }");
        }
        put(assetPath(block, "blockstates"), json.append("\n  }\n}\n").toString());
    }

    @Override
    public void selfDrop(ResourceId block) {
        String directory = dataDirectory("loot_table") + "/blocks";
        put(dataPath(block, directory), "{\n  \"type\": \"minecraft:block\",\n  \"pools\": [{\n"
                + "    \"rolls\": 1,\n    \"entries\": [{ \"type\": \"minecraft:item\", \"name\": "
                + quote(block.toString()) + " }],\n"
                + "    \"conditions\": [{ \"condition\": \"minecraft:survives_explosion\" }]\n  }]\n}\n");
    }

    @Override
    public void tag(ResourceId id, TagSpec tag) {
        String registry = tag.registry() == TagSpec.Registry.ITEMS ? "item" : "block";
        if (minecraftVersion.compareTo(SINGULAR_DATA_PATHS) < 0) {
            registry += 's';
        }
        List<ResourceId> values = new ArrayList<>(tag.values());
        values.sort(ResourceId::compareTo);
        String joined = values.stream().map(ResourceId::toString).map(JsonDataGenerationContext::quote)
                .collect(java.util.stream.Collectors.joining(", "));
        put(dataPath(id, "tags/" + registry), "{\n  \"replace\": " + tag.replace()
                + ",\n  \"values\": [" + joined + "]\n}\n");
    }

    @Override
    public void translation(String locale, String key, String value) {
        if (!locale.matches("[a-z]{2}_[a-z]{2}")) {
            throw new IllegalArgumentException("Locale must use language_country form: " + locale);
        }
        translations.computeIfAbsent(locale, ignored -> new TreeMap<>())
                .put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
    }

    public Map<String, String> resources() {
        Map<String, String> result = new TreeMap<>(resources);
        translations.forEach((locale, entries) -> {
            String body = entries.entrySet().stream()
                    .map(entry -> "  " + quote(entry.getKey()) + ": " + quote(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(",\n", "{\n", "\n}\n"));
            putInto(result, "assets/" + modNamespace + "/lang/" + locale + ".json", body);
        });
        return java.util.Collections.unmodifiableMap(result);
    }

    public void writeTo(Path outputDirectory) throws IOException {
        Path root = outputDirectory.toAbsolutePath().normalize();
        for (Map.Entry<String, String> resource : resources().entrySet()) {
            Path target = root.resolve(resource.getKey()).normalize();
            if (!target.startsWith(root)) {
                throw new IOException("Generated resource escapes output directory: " + resource.getKey());
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, resource.getValue(), StandardCharsets.UTF_8);
        }
    }

    private String dataDirectory(String singular) {
        if (minecraftVersion.compareTo(SINGULAR_DATA_PATHS) >= 0) {
            return singular;
        }
        return switch (singular) {
            case "recipe" -> "recipes";
            case "loot_table" -> "loot_tables";
            default -> singular;
        };
    }

    private String ingredient(Ingredient ingredient) {
        if (minecraftVersion.compareTo(HOLDER_SET_INGREDIENTS) >= 0) {
            String value = ingredient.kind() == Ingredient.Kind.TAG
                    ? '#' + ingredient.id().toString()
                    : ingredient.id().toString();
            return quote(value);
        }
        String key = ingredient.kind() == Ingredient.Kind.ITEM ? "item" : "tag";
        return "{ \"" + key + "\": " + quote(ingredient.id().toString()) + " }";
    }

    private String result(ResourceId item, int count) {
        String idKey = minecraftVersion.compareTo(SINGULAR_DATA_PATHS) >= 0 ? "id" : "item";
        return "{ \"" + idKey + "\": " + quote(item.toString()) + ", \"count\": " + count + " }";
    }

    private static String dataPath(ResourceId id, String directory) {
        return "data/" + id.namespace() + '/' + directory + '/' + id.path() + ".json";
    }

    private static String assetPath(ResourceId id, String directory) {
        return "assets/" + id.namespace() + '/' + directory + '/' + id.path() + ".json";
    }

    @Override
    public void machineRecipe(ResourceId id, uk.co.enderfall.sdk.api.recipe.MachineRecipeSpec recipe) {
        Objects.requireNonNull(recipe, "recipe");
        if (recipe.itemInputs().isEmpty() || recipe.itemInputs().size() > 5 || recipe.itemOutputs().size() != 1) {
            throw new IllegalArgumentException("Machine menu supports 1-5 item inputs and one item output");
        }
        String inputs = recipe.itemInputs().stream().map(entry -> "{"
                + quote(entry.ingredient().kind() == Ingredient.Kind.TAG ? "tag" : "item") + ":"
                + quote(entry.ingredient().id().toString()) + ",\"count\":" + entry.count() + "}")
                .collect(java.util.stream.Collectors.joining(","));
        String fluids = recipe.fluidInputs().stream().map(entry -> "{"
                + quote(entry.kind() == uk.co.enderfall.sdk.api.recipe.FluidIngredient.Kind.TAG ? "tag" : "fluid")
                + ":" + quote(entry.id().toString()) + ",\"amount\":" + entry.amount() + "}")
                .collect(java.util.stream.Collectors.joining(","));
        String outputs = recipe.itemOutputs().stream().map(entry -> "{\"item\":" + quote(entry.item().toString())
                + ",\"count\":" + entry.count() + "}").collect(java.util.stream.Collectors.joining(","));
        String fluidOutputs = recipe.fluidOutputs().stream().map(entry -> "{\"fluid\":" + quote(entry.fluid().toString())
                + ",\"amount\":" + entry.amount() + "}").collect(java.util.stream.Collectors.joining(","));
        String json = "{\"type\":" + quote(recipe.type().toString()) + ",\"duration_ticks\":" + recipe.durationTicks()
                + ",\"item_inputs\":[" + inputs + "],\"fluid_inputs\":[" + fluids + "],\"item_outputs\":["
                + outputs + "],\"fluid_outputs\":[" + fluidOutputs + "]}\n";
        if (json.getBytes(StandardCharsets.UTF_8).length > 65536) throw new IllegalArgumentException("Machine recipe exceeds 64 KiB");
        put(dataPath(id, "enderfall_machine"), json);
    }

    private void put(String path, String content) {
        putInto(resources, path, content);
    }

    private static void putInto(Map<String, String> destination, String path, String content) {
        if (destination.putIfAbsent(path, content) != null) {
            throw new IllegalStateException("Duplicate generated resource: " + path);
        }
    }

    private static void requireKind(ModelSpec spec, ModelSpec.Kind... allowed) {
        for (ModelSpec.Kind kind : allowed) {
            if (spec.kind() == kind) {
                return;
            }
        }
        throw new IllegalArgumentException("Unsupported model kind here: " + spec.kind());
    }

    private static String quote(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }
}
