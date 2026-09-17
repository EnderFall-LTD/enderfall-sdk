package uk.co.enderfall.sdk.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.block.BlockProperty;
import uk.co.enderfall.sdk.api.block.BlockStateDefinition;
import uk.co.enderfall.sdk.api.blockentity.BlockLocation;
import uk.co.enderfall.sdk.api.registry.BlockRef;

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
}
