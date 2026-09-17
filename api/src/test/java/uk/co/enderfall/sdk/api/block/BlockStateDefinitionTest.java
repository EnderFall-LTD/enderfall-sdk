package uk.co.enderfall.sdk.api.block;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlockStateDefinitionTest {
    enum Mode { IDLE, ACTIVE }
    @Test void stateShapesRequireMatchingSchemaAndAreNotMaterialProperties() {
        var schema = BlockStateDefinition.builder().property(BlockProperty.bool("open"), false).build();
        var shapes = BlockStateShapes.create(schema, state -> BlockShape.fullCube());
        var spec = uk.co.enderfall.sdk.api.registry.BlockSpec.builder().states(schema).stateShapes(shapes).build();
        assertSame(shapes, spec.stateShapes().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> uk.co.enderfall.sdk.api.registry.BlockSpec.builder().stateShapes(shapes).build());
        assertTrue(uk.co.enderfall.sdk.api.registry.BlockSpec.builder().copyFrom(spec).build().stateShapes().isEmpty());
    }
    @Test void blockSpecPreservesSchemaButMaterialCopyDoesNotCopyBehavior() {
        var schema = BlockStateDefinition.builder().property(BlockProperty.bool("open"), false).build();
        var spec = uk.co.enderfall.sdk.api.registry.BlockSpec.builder().states(schema).sixWayFacing().build();
        assertSame(schema, spec.states());
        assertTrue(uk.co.enderfall.sdk.api.registry.BlockSpec.builder().copyFrom(spec).build().states().properties().isEmpty());
        assertSame(schema, uk.co.enderfall.sdk.api.registry.BlockSpec.builder().states(schema).copyFrom(spec).build().states());
        var conflict = BlockStateDefinition.builder().property(BlockProperty.bool("facing"), false).build();
        assertThrows(IllegalArgumentException.class, () -> uk.co.enderfall.sdk.api.registry.BlockSpec.builder().states(conflict).horizontalFacing().build());
        var large = BlockStateDefinition.builder().property(BlockProperty.integer("a", 0, 255), 0)
                .property(BlockProperty.integer("b", 0, 3), 0).build();
        assertThrows(IllegalArgumentException.class, () -> uk.co.enderfall.sdk.api.registry.BlockSpec.builder().states(large).sixWayFacing().build());
    }

    @Test void nativeWaterloggingCountsStatesAndRejectsPropertyCollision() {
        var waterlogged = BlockStateDefinition.builder().property(BlockProperty.bool("waterlogged"), false).build();
        assertThrows(IllegalArgumentException.class, () -> uk.co.enderfall.sdk.api.registry.BlockSpec.builder()
                .states(waterlogged).waterlogged().build());
        var largeBuilder = BlockStateDefinition.builder();
        for (int i = 0; i < 12; i++) largeBuilder.property(BlockProperty.bool("flag_" + i), false);
        var large = largeBuilder.build();
        assertThrows(IllegalArgumentException.class, () -> uk.co.enderfall.sdk.api.registry.BlockSpec.builder()
                .states(large).waterlogged().sixWayFacing().build());
        var spec = uk.co.enderfall.sdk.api.registry.BlockSpec.builder().waterlogged().scheduledTicks().build();
        assertTrue(spec.waterlogged());
        assertTrue(spec.scheduledTicks());
        var copied = uk.co.enderfall.sdk.api.registry.BlockSpec.builder().copyFrom(spec).build();
        assertFalse(copied.waterlogged());
        assertFalse(copied.scheduledTicks());
    }

    @Test void completeStateRoundTripsAndRejectsMissingOrUnknownProperties() {
        var open = BlockProperty.bool("open");
        var schema = BlockStateDefinition.builder().property(open, false).build();
        var state = schema.defaultState().with(open, true);
        assertEquals(state, schema.parse(state.serializedValues()));
        assertThrows(IllegalArgumentException.class, () -> schema.parse(java.util.Map.of()));
        assertThrows(IllegalArgumentException.class, () -> schema.parse(java.util.Map.of("wrong", "true")));
        assertThrows(IllegalArgumentException.class, () -> schema.parse(java.util.Map.of("open", "yes")));
        assertThrows(IllegalArgumentException.class, () -> schema.parse(java.util.Map.of("open", "true", "extra", "false")));
    }

    @Test void shapesAreComputedOnceForEveryState() {
        var open = BlockProperty.bool("open");
        var level = BlockProperty.integer("level", 0, 2);
        var schema = BlockStateDefinition.builder().property(open, false).property(level, 0).build();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var shapes = BlockStateShapes.create(schema, state -> {
            calls.incrementAndGet();
            return state.get(open) ? BlockShape.empty() : BlockShape.box(0, 0, 0, 16, state.get(level) + 1, 16);
        });
        assertEquals(6, calls.get());
        assertEquals(BlockShape.empty(), shapes.shape(schema.defaultState().with(open, true)));
        assertEquals(BlockShape.box(0, 0, 0, 16, 3, 16), shapes.shape(schema.defaultState().with(level, 2)));
        assertEquals(6, calls.get());
        assertThrows(IllegalArgumentException.class, () -> shapes.shape(BlockStateDefinition.builder().build().defaultState()));
        assertThrows(NullPointerException.class, () -> BlockStateShapes.create(schema, state -> null));
    }

    @Test void typedStatesAreImmutableAndSerializeInDeclarationOrder() {
        var open = BlockProperty.bool("open");
        var level = BlockProperty.integer("level", 0, 15);
        var schema = BlockStateDefinition.builder().property(open, false).property(level, 3).build();
        var initial = schema.defaultState();
        var changed = initial.with(open, true);
        assertFalse(initial.get(open));
        assertTrue(changed.get(open));
        assertEquals(3, changed.get(level));
        assertEquals(32, schema.stateCount());
        assertEquals(java.util.List.of("open", "level"), java.util.List.copyOf(changed.serializedValues().keySet()));
        assertEquals("true", changed.serializedValues().get("open"));
        assertEquals(changed, initial.with(open, true));
        assertEquals(changed.hashCode(), initial.with(open, true).hashCode());
        assertSame(initial, initial.with(open, false));
        assertThrows(UnsupportedOperationException.class, () -> changed.serializedValues().clear());
        assertThrows(IllegalArgumentException.class, () -> changed.with(level, 16));
        assertThrows(IllegalArgumentException.class, () -> changed.get(BlockProperty.bool("open")));
    }

    @Test void boundedSchemaRejectsDuplicatesAndDoesNotMutateOnFailure() {
        var builder = BlockStateDefinition.builder();
        for (int i = 0; i < 12; i++) builder.property(BlockProperty.bool("flag_" + i), false);
        var snapshot = builder.build();
        assertEquals(4096, snapshot.stateCount());
        assertThrows(IllegalArgumentException.class, () -> builder.property(BlockProperty.bool("overflow"), false));
        assertThrows(IllegalArgumentException.class, () -> builder.property(BlockProperty.bool("flag_0"), true));
        assertEquals(4096, builder.build().stateCount());
        assertEquals(12, snapshot.properties().size());
    }

    @Test void schemaSnapshotsDoNotFollowBuilderChanges() {
        var builder = BlockStateDefinition.builder();
        var empty = builder.build();
        builder.property(BlockProperty.bool("open"), false);
        assertTrue(empty.properties().isEmpty());
        assertEquals(1, empty.stateCount());
    }

    @Test void propertyCodecsRejectAmbiguousOrInvalidValues() {
        var mode = BlockProperty.enumeration("mode", Mode.class, value -> value == Mode.IDLE ? "idle" : "running");
        assertEquals(Mode.ACTIVE, mode.parse("running"));
        assertEquals("idle", mode.serialize(Mode.IDLE));
        assertThrows(IllegalArgumentException.class, () -> mode.parse("ACTIVE"));
        assertThrows(IllegalArgumentException.class, () -> BlockProperty.enumeration("mode", Mode.class, value -> "same"));
        assertThrows(IllegalArgumentException.class, () -> BlockProperty.bool("Bad Name"));
        assertThrows(IllegalArgumentException.class, () -> BlockProperty.integer("level", 0, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> BlockProperty.integer("level", -1, 2));
        assertThrows(IllegalArgumentException.class, () -> BlockProperty.integer("level", 1, 1));
        assertThrows(UnsupportedOperationException.class, () -> mode.values().clear());
        assertThrows(IllegalArgumentException.class, () -> BlockStateDefinition.builder().property(BlockProperty.integer("level", 0, 3), 4));
    }
}
