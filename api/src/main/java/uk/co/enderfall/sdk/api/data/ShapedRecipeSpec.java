package uk.co.enderfall.sdk.api.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ShapedRecipeSpec {
    private final List<String> pattern;
    private final Map<Character, Ingredient> keys;
    private final RecipeResult result;

    public ShapedRecipeSpec(List<String> pattern, Map<Character, Ingredient> keys, RecipeResult result) {
        this.pattern = List.copyOf(pattern);
        this.keys = Map.copyOf(new LinkedHashMap<>(keys));
        this.result = Objects.requireNonNull(result, "result");
        validate();
    }

    private void validate() {
        if (pattern.isEmpty() || pattern.size() > 3) {
            throw new IllegalArgumentException("A shaped recipe needs one to three rows");
        }
        int width = pattern.get(0).length();
        if (width < 1 || width > 3 || pattern.stream().anyMatch(row -> row.length() != width)) {
            throw new IllegalArgumentException("Shaped recipe rows must have the same width from one to three");
        }
        for (String row : pattern) {
            row.chars().filter(character -> character != ' ').forEach(character -> {
                if (!keys.containsKey((char) character)) {
                    throw new IllegalArgumentException("Pattern uses undefined key: " + (char) character);
                }
            });
        }
    }

    public List<String> pattern() {
        return pattern;
    }

    public Map<Character, Ingredient> keys() {
        return keys;
    }

    public RecipeResult result() {
        return result;
    }
}
