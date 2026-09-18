package uk.co.enderfall.sdk.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.block.BlockProperty;
import uk.co.enderfall.sdk.api.block.BlockStateDefinition;
import uk.co.enderfall.sdk.api.block.BlockToolRef;
import uk.co.enderfall.sdk.api.blockentity.BlockLocation;
import uk.co.enderfall.sdk.api.registry.BlockRef;
import uk.co.enderfall.sdk.api.registry.BlockSpec;

class DefaultBlockStateManagerTest {
    @Test void delegatesTypedReadsAndUpdatesWithoutWeakeningValidation() {
        var open = BlockProperty.bool("open");
        var state = BlockStateDefinition.builder().property(open, false).build().defaultState();
        var updates = new AtomicInteger();
        PlatformAdapter adapter = (PlatformAdapter) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { PlatformAdapter.class }, (proxy, method, arguments) -> switch (method.getName()) {
                    case "blockState" -> Optional.of(state);
                    case "updateBlockState" -> {
                        @SuppressWarnings("unchecked")
                        var change = (java.util.function.UnaryOperator<uk.co.enderfall.sdk.api.block.PortableBlockState>) arguments[2];
                        assertTrue(change.apply(state).get(open));
                        updates.incrementAndGet();
                        yield true;
                    }
                    default -> method.isDefault() ? java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, arguments) : null;
                });
        var manager = new DefaultBlockStateManager(adapter);
        var block = new BlockRef(ResourceId.of("test_mod", "barrel"));
        var location = new BlockLocation(ResourceId.of("minecraft", "overworld"), 1, 2, 3);
        assertSame(state, manager.get(block, location).orElseThrow());
        assertTrue(manager.set(block, location, open, true));
        assertEquals(1, updates.get());
        assertThrows(NullPointerException.class, () -> manager.get(null, location));
        assertThrows(NullPointerException.class, () -> manager.update(block, location, null));
    }

    @Test void selectsAndCyclesOnlyPropertiesDeclaredForTheTool() {
        var open = BlockProperty.bool("open");
        var level = BlockProperty.integer("level", 0, 2);
        var schema = BlockStateDefinition.builder().property(open, false).property(level, 0).build();
        var state = new AtomicReference<>(schema.defaultState());
        PlatformAdapter adapter = (PlatformAdapter) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { PlatformAdapter.class }, (proxy, method, arguments) -> switch (method.getName()) {
                    case "supportsBlockStates" -> true;
                    case "registerBlock" -> null;
                    case "blockState" -> Optional.of(state.get());
                    case "updateBlockState" -> {
                        @SuppressWarnings("unchecked")
                        var change = (java.util.function.UnaryOperator<uk.co.enderfall.sdk.api.block.PortableBlockState>) arguments[2];
                        var previous = state.get();
                        var replacement = change.apply(previous);
                        state.set(replacement);
                        yield !replacement.equals(previous);
                    }
                    default -> method.isDefault()
                            ? java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, arguments) : null;
                });
        var gate = new RegistrationGate();
        var items = new DefaultItemRegistrar("test_mod", "test-target", adapter, gate);
        var blocks = new DefaultBlockRegistrar("test_mod", "test-target", adapter, gate, items);
        var block = blocks.register("machine", BlockSpec.builder().states(schema)
                .toolProperties(BlockToolRef.of("test_mod", "wrench"), open, level).build());
        var manager = new DefaultBlockStateManager(adapter, blocks);
        var location = new BlockLocation(ResourceId.of("minecraft", "overworld"), 1, 2, 3);
        var wrench = BlockToolRef.of("test_mod", "wrench");

        var selected = manager.selectNextToolProperty(block, location, wrench, 0).orElseThrow();
        assertEquals(1, selected.selectionIndex());
        assertEquals("level", selected.propertyName());
        assertFalse(selected.changed());
        assertEquals(0, manager.selectNextToolProperty(
                block, location, wrench, Integer.MAX_VALUE).orElseThrow().selectionIndex());
        var changed = manager.cycleToolProperty(block, location, wrench, selected.selectionIndex()).orElseThrow();
        assertEquals("0", changed.previousValue());
        assertEquals("1", changed.value());
        assertTrue(changed.changed());
        assertEquals(1, state.get().get(level));
        assertTrue(manager.cycleToolProperty(block, location,
                BlockToolRef.of("test_mod", "hammer"), 0).isEmpty());
    }
}
