package uk.co.enderfall.sdk.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;
import uk.co.enderfall.sdk.api.blockentity.BlockLocation;
import uk.co.enderfall.sdk.api.registry.BlockRef;
import uk.co.enderfall.sdk.api.ui.StorageContainerSpec;

class DefaultStorageContainerManagerTest {
    private static final String MOD = "test_mod";
    private static final String TARGET = "1.21.4-fabric";
    private static final BlockEntitySpec STORAGE = BlockEntitySpec.builder(
            new BlockRef(ResourceId.of(MOD, "cabinet"))).inventorySlots(27).build();

    private record Fixture(DefaultStorageContainerManager manager, RegistrationGate gate,
                           List<String> calls, Object[][] arguments) { }

    private static Fixture fixture(boolean supported) {
        var calls = new ArrayList<String>();
        var arguments = new Object[2][];
        PlatformAdapter adapter = (PlatformAdapter) Proxy.newProxyInstance(
                PlatformAdapter.class.getClassLoader(), new Class<?>[] {PlatformAdapter.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("supportsStorageContainers")) return supported;
                    calls.add(method.getName());
                    arguments[Math.min(calls.size() - 1, 1)] = args;
                    return null;
                });
        var gate = new RegistrationGate();
        return new Fixture(new DefaultStorageContainerManager(MOD, TARGET, adapter, gate), gate, calls, arguments);
    }

    @Test void registersAndOpensOnlyKnownOwnedContainers() {
        var fixture = fixture(true);
        var spec = StorageContainerSpec.builder("Cabinet", STORAGE).build();
        var ref = fixture.manager.register("cabinet", spec);
        var player = UUID.randomUUID();
        var location = new BlockLocation(ResourceId.of("minecraft", "overworld"), 3, 70, -8);
        fixture.manager.openAt(player, ref, location);
        assertEquals(List.of("registerStorageContainer", "openStorageContainer"), fixture.calls);
        assertEquals(player, fixture.arguments[1][0]);
        assertSame(location, fixture.arguments[1][2]);
        assertThrows(IllegalArgumentException.class, () -> fixture.manager.openAt(player,
                new uk.co.enderfall.sdk.api.ui.StorageContainerRef(ResourceId.of(MOD, "missing")), location));
    }

    @Test void rejectsUnsupportedForeignDuplicateAndLateRegistrationsBeforeNativeWrites() {
        var unsupported = fixture(false);
        var spec = StorageContainerSpec.builder("Cabinet", STORAGE).build();
        var error = assertThrows(UnsupportedOperationException.class,
                () -> unsupported.manager.register("cabinet", spec));
        assertTrue(error.getMessage().contains(MOD));
        assertTrue(error.getMessage().contains(TARGET));
        assertTrue(unsupported.calls.isEmpty());

        var supported = fixture(true);
        supported.manager.register("cabinet", spec);
        assertThrows(IllegalStateException.class, () -> supported.manager.register("cabinet", spec));
        var foreign = BlockEntitySpec.builder(new BlockRef(ResourceId.of("other_mod", "cabinet")))
                .inventorySlots(27).build();
        assertThrows(IllegalArgumentException.class, () -> supported.manager.register("foreign",
                StorageContainerSpec.builder("Foreign", foreign).build()));
        supported.gate.freeze();
        assertThrows(IllegalStateException.class, () -> supported.manager.register("late", spec));
        assertEquals(List.of("registerStorageContainer"), supported.calls);
    }
}
