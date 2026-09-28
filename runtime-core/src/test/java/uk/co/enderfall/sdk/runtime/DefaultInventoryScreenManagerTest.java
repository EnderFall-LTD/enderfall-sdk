package uk.co.enderfall.sdk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.client.ui.InventoryScreenContext;
import uk.co.enderfall.sdk.api.client.ui.PortableInventoryScreen;
import uk.co.enderfall.sdk.api.client.ui.UiRenderContext;
import uk.co.enderfall.sdk.api.platform.Environment;
import uk.co.enderfall.sdk.api.platform.Loader;
import uk.co.enderfall.sdk.api.platform.MinecraftVersion;
import uk.co.enderfall.sdk.api.platform.PlatformInfo;
import uk.co.enderfall.sdk.api.ui.StorageContainerRef;

class DefaultInventoryScreenManagerTest {
    private static final StorageContainerRef CONTAINER =
            new StorageContainerRef(ResourceId.of("test_mod", "cabinet"));

    @Test void validatesClientOwnershipDuplicatesAndFreeze() {
        AtomicInteger registrations = new AtomicInteger();
        RegistrationGate gate = new RegistrationGate();
        var manager = new DefaultInventoryScreenManager("test_mod", "1.21.4-fabric",
                adapter(Environment.CLIENT, registrations), gate);
        assertThrows(IllegalArgumentException.class, () -> manager.register(
                new StorageContainerRef(ResourceId.of("other_mod", "cabinet")), View::new));
        manager.register(CONTAINER, View::new);
        assertEquals(1, registrations.get());
        assertThrows(IllegalStateException.class, () -> manager.register(CONTAINER, View::new));
        gate.freeze();
        assertThrows(IllegalStateException.class, () -> manager.register(
                new StorageContainerRef(ResourceId.of("test_mod", "later")), View::new));
    }

    @Test void rejectsDedicatedServerUse() {
        AtomicInteger registrations = new AtomicInteger();
        var manager = new DefaultInventoryScreenManager("test_mod", "1.21.4-fabric",
                adapter(Environment.DEDICATED_SERVER, registrations), new RegistrationGate());
        assertThrows(IllegalStateException.class, () -> manager.register(CONTAINER, View::new));
        assertEquals(0, registrations.get());
    }

    private static PlatformAdapter adapter(Environment environment, AtomicInteger registrations) {
        return (PlatformAdapter) Proxy.newProxyInstance(PlatformAdapter.class.getClassLoader(),
                new Class<?>[]{PlatformAdapter.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "platformInfo" -> new Info(environment);
                    case "registerInventoryScreen" -> { registrations.incrementAndGet(); yield null; }
                    default -> throw new AssertionError("Unexpected adapter operation: " + method.getName());
                });
    }

    private static final class View implements PortableInventoryScreen {
        @Override public void renderBackground(UiRenderContext graphics, InventoryScreenContext context) { }
    }

    private record Info(Environment environment) implements PlatformInfo {
        @Override public Loader loader() { return Loader.FABRIC; }
        @Override public MinecraftVersion minecraftVersion() { return new MinecraftVersion("1.21.4"); }
        @Override public boolean isModLoaded(String id) { return false; }
        @Override public Optional<String> modVersion(String id) { return Optional.empty(); }
    }
}
