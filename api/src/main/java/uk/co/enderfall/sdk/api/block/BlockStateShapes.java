package uk.co.enderfall.sdk.api.block;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/**
 * Precomputes geometry for a finite schema. Callbacks run once per state at definition
 * time, never during collision or rendering. Geometry uses model coordinates.
 */
@Experimental("Portable state-dependent geometry")
public final class BlockStateShapes {
    private final BlockStateDefinition definition;
    private final Map<PortableBlockState, BlockShape> shapes;

    private BlockStateShapes(BlockStateDefinition definition, Map<PortableBlockState, BlockShape> shapes) {
        this.definition = definition;
        this.shapes = Map.copyOf(shapes);
    }

    public static BlockStateShapes create(BlockStateDefinition definition,
            Function<PortableBlockState, BlockShape> geometry) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(geometry, "geometry");
        var shapes = new LinkedHashMap<PortableBlockState, BlockShape>();
        for (PortableBlockState state : definition.states()) {
            shapes.put(state, Objects.requireNonNull(geometry.apply(state), "geometry returned null"));
        }
        return new BlockStateShapes(definition, shapes);
    }

    public BlockStateDefinition definition() { return definition; }
    public BlockShape shape(PortableBlockState state) {
        Objects.requireNonNull(state, "state");
        if (state.definition() != definition) throw new IllegalArgumentException("State belongs to another definition");
        return shapes.get(state);
    }
}
