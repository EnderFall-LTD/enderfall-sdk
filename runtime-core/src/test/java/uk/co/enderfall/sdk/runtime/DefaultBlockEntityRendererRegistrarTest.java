package uk.co.enderfall.sdk.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.platform.*;
import uk.co.enderfall.sdk.api.registry.BlockRef;
import uk.co.enderfall.sdk.api.render.*;

class DefaultBlockEntityRendererRegistrarTest {
    private static final BlockRef BLOCK = new BlockRef(ResourceId.of("test_mod", "board"));
    private static final BlockEntityRenderSpec SPEC = BlockEntityRenderSpec.builder()
            .inventorySlot(0, RenderTransform.at(0.5f, 1, 0.5f)).build();

    @Test void rejectsWrongOwnerDuplicatesAndLateRegistration() {
        var calls = new AtomicInteger();
        var gate = new RegistrationGate();
        var registrar = new DefaultBlockEntityRendererRegistrar("test_mod", "1.21.4-fabric",
                adapter(Environment.CLIENT, new AtomicBoolean(true), calls), gate);
        assertThrows(IllegalArgumentException.class, () -> registrar.register(
                new BlockRef(ResourceId.of("other_mod", "board")), SPEC));
        registrar.register(BLOCK, SPEC);
        assertEquals(1, calls.get());
        assertThrows(IllegalStateException.class, () -> registrar.register(BLOCK, SPEC));
        gate.freeze();
        assertThrows(IllegalStateException.class, () -> registrar.register(
                new BlockRef(ResourceId.of("test_mod", "another")), SPEC));
        assertEquals(1, calls.get());
    }

    @Test void fluidRegistrationDelegatesSupportToNativeBridge() {
        var calls = new AtomicInteger();
        var registrar = new DefaultBlockEntityRendererRegistrar("test_mod", "1.21.4-fabric",
                adapter(Environment.CLIENT, new AtomicBoolean(true), calls), new RegistrationGate());
        var fluid = BlockEntityRenderSpec.builder().fluid(
                new uk.co.enderfall.sdk.api.fluid.FluidTankSpec("tank", 81000),
                new FluidRenderBounds(.1, .1, .1, .9, .9, .9)).build();
        assertDoesNotThrow(() -> registrar.register(BLOCK, fluid));
        assertEquals(1, calls.get());
        assertThrows(IllegalStateException.class, () -> registrar.register(BLOCK, SPEC));
    }
    @Test void unsupportedModelPlansDoNotReserveBlock() {
        var calls = new AtomicInteger();
        var supported = new AtomicBoolean(false);
        var registrar = new DefaultBlockEntityRendererRegistrar("test_mod", "1.21.4-fabric",
                adapter(Environment.CLIENT, supported, calls), new RegistrationGate());
        var plan = BlockEntityRenderSpec.builder().model(new ModelRef(ResourceId.of("test_mod", "block/lid")),
                RenderTransform.at(0, 0, 0)).build();
        var error = assertThrows(UnsupportedOperationException.class, () -> registrar.register(BLOCK, plan));
        assertTrue(error.getMessage().contains("test_mod on 1.21.4-fabric"));
        assertEquals(1, calls.get());
        supported.set(true);
        registrar.register(BLOCK, SPEC);
        assertEquals(2, calls.get());
    }

    @Test void rejectsServerAndDoesNotReserveFailedRegistration() {
        var calls = new AtomicInteger();
        var supported = new AtomicBoolean(false);
        var server = new DefaultBlockEntityRendererRegistrar("test_mod", "1.21.4-fabric",
                adapter(Environment.DEDICATED_SERVER, supported, calls), new RegistrationGate());
        assertThrows(IllegalStateException.class, () -> server.register(BLOCK, SPEC));
        assertEquals(0, calls.get());
        var client = new DefaultBlockEntityRendererRegistrar("test_mod", "1.21.4-fabric",
                adapter(Environment.CLIENT, supported, calls), new RegistrationGate());
        var failure = assertThrows(UnsupportedOperationException.class, () -> client.register(BLOCK, SPEC));
        assertTrue(failure.getMessage().contains("test_mod on 1.21.4-fabric"));
        supported.set(true);
        assertDoesNotThrow(() -> client.register(BLOCK, SPEC));
        assertEquals(2, calls.get());
    }

    private static PlatformAdapter adapter(Environment environment, AtomicBoolean supported, AtomicInteger calls) {
        return (PlatformAdapter) Proxy.newProxyInstance(PlatformAdapter.class.getClassLoader(),
                new Class<?>[]{PlatformAdapter.class}, (proxy, method, args) -> {
                    if (method.getName().equals("platformInfo")) return new Info(environment);
                    if (method.getName().equals("registerBlockEntityRenderer")) {
                        calls.incrementAndGet();
                        if (!supported.get()) throw new UnsupportedOperationException("Not implemented");
                        return null;
                    }
                    throw new AssertionError("Unexpected adapter operation: " + method.getName());
                });
    }

    private record Info(Environment environment) implements PlatformInfo {
        @Override public Loader loader() { return Loader.FABRIC; }
        @Override public MinecraftVersion minecraftVersion() { return new MinecraftVersion("1.21.4"); }
        @Override public boolean isModLoaded(String id) { return false; }
        @Override public Optional<String> modVersion(String id) { return Optional.empty(); }
    }
}
