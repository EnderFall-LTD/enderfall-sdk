package uk.co.enderfall.sdk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.client.ui.ClientScreenRef;
import uk.co.enderfall.sdk.api.client.ui.ClientScreenSpec;
import uk.co.enderfall.sdk.api.client.ui.PortableClientScreen;
import uk.co.enderfall.sdk.api.client.ui.UiRenderContext;
import uk.co.enderfall.sdk.api.platform.Environment;
import uk.co.enderfall.sdk.api.platform.Loader;
import uk.co.enderfall.sdk.api.platform.MinecraftVersion;
import uk.co.enderfall.sdk.api.platform.PlatformInfo;

class DefaultClientScreenManagerTest {
    private static final ResourceId ID = ResourceId.of("test_mod", "catalogue");
    private static final ClientScreenSpec SPEC = ClientScreenSpec.of("Catalogue");

    @Test void validatesOwnershipDuplicatesAndRegistrationFreeze() {
        var calls = new Calls();
        var gate = new RegistrationGate();
        var manager = new DefaultClientScreenManager("test_mod", "1.21.4-fabric",
                adapter(Environment.CLIENT, calls), gate);
        assertThrows(IllegalArgumentException.class, () -> manager.register(
                ResourceId.of("other_mod", "catalogue"), SPEC, Screen::new));
        assertEquals(new ClientScreenRef(ID), manager.register(ID, SPEC, Screen::new));
        assertEquals(1, calls.registrations.get());
        assertThrows(IllegalStateException.class, () -> manager.register(ID, SPEC, Screen::new));
        gate.freeze();
        assertThrows(IllegalStateException.class, () -> manager.register(
                ResourceId.of("test_mod", "later"), SPEC, Screen::new));
    }

    @Test void failedRegistrationDoesNotReserveIdentifier() {
        var calls = new Calls();
        calls.supported.set(false);
        var manager = new DefaultClientScreenManager("test_mod", "1.21.4-fabric",
                adapter(Environment.CLIENT, calls), new RegistrationGate());
        var error = assertThrows(UnsupportedOperationException.class,
                () -> manager.register(ID, SPEC, Screen::new));
        assertTrue(error.getMessage().contains("test_mod on 1.21.4-fabric"));
        calls.supported.set(true);
        manager.register(ID, SPEC, Screen::new);
        assertEquals(2, calls.registrations.get());
    }

    @Test void opensOnlyRegisteredScreensAndDelegatesClose() {
        var calls = new Calls();
        var manager = new DefaultClientScreenManager("test_mod", "1.21.4-fabric",
                adapter(Environment.CLIENT, calls), new RegistrationGate());
        assertThrows(IllegalArgumentException.class,
                () -> manager.open(new ClientScreenRef(ID)));
        ClientScreenRef screen = manager.register(ID, SPEC, Screen::new);
        manager.open(screen);
        manager.close();
        assertEquals(1, calls.opens.get());
        assertEquals(1, calls.closes.get());
    }

    @Test void rejectsDedicatedServerUse() {
        var calls = new Calls();
        var manager = new DefaultClientScreenManager("test_mod", "1.21.4-fabric",
                adapter(Environment.DEDICATED_SERVER, calls), new RegistrationGate());
        assertThrows(IllegalStateException.class, () -> manager.register(ID, SPEC, Screen::new));
        assertThrows(IllegalStateException.class, () -> manager.open(new ClientScreenRef(ID)));
        assertThrows(IllegalStateException.class, manager::close);
        assertEquals(0, calls.registrations.get());
    }

    private static PlatformAdapter adapter(Environment environment, Calls calls) {
        return (PlatformAdapter) Proxy.newProxyInstance(PlatformAdapter.class.getClassLoader(),
                new Class<?>[]{PlatformAdapter.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "platformInfo" -> new Info(environment);
                    case "registerClientScreen" -> {
                        calls.registrations.incrementAndGet();
                        if (!calls.supported.get()) throw new UnsupportedOperationException("Not implemented");
                        yield null;
                    }
                    case "openClientScreen" -> { calls.opens.incrementAndGet(); yield null; }
                    case "closeClientScreen" -> { calls.closes.incrementAndGet(); yield null; }
                    default -> throw new AssertionError("Unexpected adapter operation: " + method.getName());
                });
    }

    private static final class Calls {
        private final AtomicBoolean supported = new AtomicBoolean(true);
        private final AtomicInteger registrations = new AtomicInteger();
        private final AtomicInteger opens = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();
    }

    private static final class Screen implements PortableClientScreen {
        @Override public void render(UiRenderContext graphics) { }
    }

    private record Info(Environment environment) implements PlatformInfo {
        @Override public Loader loader() { return Loader.FABRIC; }
        @Override public MinecraftVersion minecraftVersion() { return new MinecraftVersion("1.21.4"); }
        @Override public boolean isModLoaded(String id) { return false; }
        @Override public Optional<String> modVersion(String id) { return Optional.empty(); }
    }
}
