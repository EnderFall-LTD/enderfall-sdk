package uk.co.enderfall.sdk.api.block;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/** Immutable schema; declaration order is retained for deterministic generation. */
@Experimental("Portable block-state definitions")
public final class BlockStateDefinition {
    public static final int MAX_STATES = 4096;
    private final Map<BlockProperty<?>, Object> defaults;
    private final int stateCount;

    private BlockStateDefinition(Map<BlockProperty<?>, Object> defaults, int stateCount) {
        this.defaults = Collections.unmodifiableMap(new LinkedHashMap<>(defaults));
        this.stateCount = stateCount;
    }
    public static Builder builder() { return new Builder(); }
    public int stateCount() { return stateCount; }
    public java.util.List<BlockProperty<?>> properties() { return java.util.List.copyOf(defaults.keySet()); }
    public PortableBlockState defaultState() { return new PortableBlockState(this, defaults); }

    /** Enumerates the bounded Cartesian product in property/value declaration order. */
    public java.util.List<PortableBlockState> states() {
        var states = java.util.List.of(defaultState());
        for (BlockProperty<?> property : properties()) {
            var expanded = new java.util.ArrayList<PortableBlockState>();
            for (PortableBlockState state : states) expand(expanded, state, property);
            states = expanded;
        }
        return java.util.List.copyOf(states);
    }
    private static <T> void expand(java.util.List<PortableBlockState> output,
            PortableBlockState state, BlockProperty<T> property) {
        for (T value : property.values()) output.add(state.with(property, value));
    }

    /** Decodes a complete state. Unknown or missing properties are errors, never silent defaults. */
    public PortableBlockState parse(Map<String, String> encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.size() != defaults.size()) {
            throw new IllegalArgumentException("Expected exactly " + defaults.size() + " block-state properties");
        }
        var decoded = new LinkedHashMap<BlockProperty<?>, Object>();
        for (BlockProperty<?> property : defaults.keySet()) {
            String value = encoded.get(property.name());
            if (value == null) throw new IllegalArgumentException("Missing block property: " + property.name());
            decoded.put(property, property.parse(value));
        }
        return new PortableBlockState(this, decoded);
    }

    public static final class Builder {
        private final Map<BlockProperty<?>, Object> defaults = new LinkedHashMap<>();
        private int stateCount = 1;
        public <T> Builder property(BlockProperty<T> property, T defaultValue) {
            Objects.requireNonNull(property, "property").serialize(defaultValue);
            if (defaults.keySet().stream().anyMatch(key -> key.name().equals(property.name()))) {
                throw new IllegalArgumentException("Duplicate block property: " + property.name());
            }
            int next = stateCount * property.values().size();
            if (next > MAX_STATES) throw new IllegalArgumentException("Block state combinations exceed " + MAX_STATES);
            defaults.put(property, defaultValue);
            stateCount = next;
            return this;
        }
        public BlockStateDefinition build() { return new BlockStateDefinition(defaults, stateCount); }
    }
}
