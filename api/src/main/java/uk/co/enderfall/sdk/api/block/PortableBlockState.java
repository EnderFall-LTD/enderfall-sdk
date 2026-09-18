package uk.co.enderfall.sdk.api.block;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/** Pure immutable state value, not a live world handle. */
@Experimental("Portable block-state definitions")
public final class PortableBlockState {
    private final BlockStateDefinition definition;
    private final Map<BlockProperty<?>, Object> values;
    PortableBlockState(BlockStateDefinition definition, Map<BlockProperty<?>, Object> values) {
        this.definition = definition;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
    public BlockStateDefinition definition() { return definition; }
    @SuppressWarnings("unchecked")
    public <T> T get(BlockProperty<T> property) {
        require(property);
        return (T) values.get(property);
    }
    public <T> PortableBlockState with(BlockProperty<T> property, T value) {
        require(property);
        property.serialize(value);
        if (Objects.equals(values.get(property), value)) return this;
        var updated = new LinkedHashMap<>(values);
        updated.put(property, value);
        return new PortableBlockState(definition, updated);
    }
    /** Cycles one declared property in its deterministic declaration order. */
    public <T> PortableBlockState cycle(BlockProperty<T> property) {
        return with(property, property.next(get(property)));
    }
    public Map<String, String> serializedValues() {
        var result = new LinkedHashMap<String, String>();
        for (BlockProperty<?> property : values.keySet()) encode(result, property);
        return Collections.unmodifiableMap(result);
    }
    private <T> void encode(Map<String, String> result, BlockProperty<T> property) {
        result.put(property.name(), property.serialize(get(property)));
    }
    private void require(BlockProperty<?> property) {
        if (!values.containsKey(Objects.requireNonNull(property, "property"))) {
            throw new IllegalArgumentException("Property is not part of this definition: " + property.name());
        }
    }
    @Override public boolean equals(Object other) {
        return other instanceof PortableBlockState state && definition == state.definition && values.equals(state.values);
    }
    @Override public int hashCode() { return 31 * System.identityHashCode(definition) + values.hashCode(); }
}
