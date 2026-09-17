package uk.co.enderfall.sdk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.api.network.PacketDirection;
import uk.co.enderfall.sdk.api.platform.CapabilitySet;
import uk.co.enderfall.sdk.api.platform.Environment;
import uk.co.enderfall.sdk.api.platform.Loader;
import uk.co.enderfall.sdk.api.platform.MinecraftVersion;
import uk.co.enderfall.sdk.api.platform.PlatformInfo;
import uk.co.enderfall.sdk.api.registry.BlockSpec;
import uk.co.enderfall.sdk.api.registry.CreativeTabSpec;
import uk.co.enderfall.sdk.api.registry.ItemSpec;
import uk.co.enderfall.sdk.api.ui.MenuButton;
import uk.co.enderfall.sdk.api.ui.MenuRef;
import uk.co.enderfall.sdk.api.ui.MenuSpec;
import uk.co.enderfall.sdk.api.ui.MenuState;

class DefaultMenuManagerTest {
    @Test void tankBindingRequiresOwnedBlockAndNativeSupport() {
        var adapter = new LinkedAdapter(Environment.DEDICATED_SERVER);
        var menus = manager(adapter);
        var menu = menus.register("tank", MenuSpec.builder("Tank").build(), ignored -> { });
        var tank = new uk.co.enderfall.sdk.api.fluid.FluidTankSpec("reservoir", 81000);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> menus.bindTank(menu, new uk.co.enderfall.sdk.api.registry.BlockRef(
                        ResourceId.of("another_mod", "tank")), tank));
        // This test adapter intentionally has no native tank hook: do not silently fall back.
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> menus.bindTank(menu, new uk.co.enderfall.sdk.api.registry.BlockRef(
                        ResourceId.of("test_mod", "tank")), tank));
    }
    @Test void liveStatePollsOnlyServerEndTicksAndStopsWhenUnavailable() {
        var serverAdapter = new LinkedAdapter(Environment.DEDICATED_SERVER);
        var clientAdapter = new LinkedAdapter(Environment.CLIENT);
        serverAdapter.peer = clientAdapter;
        clientAdapter.peer = serverAdapter;
        var events = new DefaultEventBus(new SystemModLogger("test_mod"));
        var server = new DefaultMenuManager("test_mod", "1.21.4-fabric", serverAdapter,
                new RegistrationGate(), new SystemModLogger("test_mod"), events);
        var client = manager(clientAdapter);
        var spec = MenuSpec.builder("Live tank").build();
        var ref = server.register("live", spec, ignored -> { });
        client.register("live", spec, ignored -> { });
        var state = new java.util.concurrent.atomic.AtomicReference<>(Optional.of(
                MenuState.builder().value("tank.percent", 25).build()));
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        server.openLive(PLAYER, ref, 2, () -> { reads.incrementAndGet(); return state.get(); });
        assertEquals(1, reads.get());
        events.publish(uk.co.enderfall.sdk.api.event.SdkEvents.TICK,
                new uk.co.enderfall.sdk.api.event.TickEvent(uk.co.enderfall.sdk.api.event.TickEvent.Side.CLIENT,
                        uk.co.enderfall.sdk.api.event.TickEvent.Phase.END, 1));
        tick(events);
        assertEquals(1, reads.get());
        tick(events);
        assertEquals(2, reads.get());
        org.junit.jupiter.api.Assertions.assertNull(clientAdapter.updated); // No redundant packet.
        state.set(Optional.of(MenuState.builder().value("tank.percent", 75).build()));
        tick(events);
        tick(events);
        assertEquals("75", clientAdapter.updated.value("tank.percent"));
        state.set(Optional.empty());
        tick(events);
        tick(events);
        assertFalse(server.isOpen(PLAYER, ref));
        int finalReads = reads.get();
        tick(events);
        tick(events);
        assertEquals(finalReads, reads.get());
    }

    @Test void failedSourceClosesAndReplacementDoesNotRetainOldSource() {
        var adapter = new LinkedAdapter(Environment.DEDICATED_SERVER);
        var peer = new LinkedAdapter(Environment.CLIENT);
        adapter.peer = peer;
        peer.peer = adapter;
        var events = new DefaultEventBus(new SystemModLogger("test_mod"));
        var server = new DefaultMenuManager("test_mod", "1.21.4-fabric", adapter,
                new RegistrationGate(), new SystemModLogger("test_mod"), events);
        var ref = server.register("live", MenuSpec.builder("Live").build(), ignored -> { });
        manager(peer).register("live", MenuSpec.builder("Live").build(), ignored -> { });
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        uk.co.enderfall.sdk.api.ui.MenuStateSource source = () -> {
            if (reads.incrementAndGet() > 1) throw new IllegalStateException("Unavailable block");
            return Optional.of(MenuState.empty());
        };
        server.openLive(PLAYER, ref, 1, source);
        tick(events);
        assertFalse(server.isOpen(PLAYER, ref));
        reads.set(0);
        server.openLive(PLAYER, ref, 1, source);
        server.open(PLAYER, ref, MenuState.empty());
        tick(events);
        assertEquals(1, reads.get());
        assertTrue(server.isOpen(PLAYER, ref));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> server.openLive(PLAYER, ref, 0, source));
        server.openLive(PLAYER, ref, 1, Optional::empty);
        assertTrue(server.isOpen(PLAYER, ref));
        for (int cleanup = 0; cleanup < 3; cleanup++) {
            reads.set(0);
            server.openLive(PLAYER, ref, 1, source);
            if (cleanup == 0) {
                peer.closeSender.run();
            } else if (cleanup == 1) {
                events.publish(uk.co.enderfall.sdk.api.event.SdkEvents.PLAYER,
                        new uk.co.enderfall.sdk.api.event.PlayerEvent(
                                uk.co.enderfall.sdk.api.event.PlayerEvent.Action.LEAVE, PLAYER, "Test"));
            } else {
                events.publish(uk.co.enderfall.sdk.api.event.SdkEvents.LIFECYCLE,
                        new uk.co.enderfall.sdk.api.event.LifecycleEvent(
                                uk.co.enderfall.sdk.api.event.LifecycleEvent.Stage.SERVER_STOPPING));
            }
            tick(events);
            assertEquals(1, reads.get());
            assertFalse(server.isOpen(PLAYER, ref));
        }
    }

    private static void tick(DefaultEventBus events) {
        events.publish(uk.co.enderfall.sdk.api.event.SdkEvents.TICK,
                new uk.co.enderfall.sdk.api.event.TickEvent(uk.co.enderfall.sdk.api.event.TickEvent.Side.SERVER,
                        uk.co.enderfall.sdk.api.event.TickEvent.Phase.END, 1));
    }

    @Test void unsupportedGaugeFailsBeforeReservingMenuId() {
        var menus = manager(new LinkedAdapter(Environment.DEDICATED_SERVER));
        var spec = MenuSpec.builder("Tank").gauge(new uk.co.enderfall.sdk.api.ui.MenuGauge(
                "tank.percent", 10, 30, 20, 60, 0xFF4488FF)).build();
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> menus.register("tank", spec, ignored -> { }));
        assertNotNull(menus.register("tank", MenuSpec.builder("Tank").build(), ignored -> { }));
    }
    private static final UUID PLAYER = UUID.fromString("21ea222f-69aa-4b75-85b3-272c7f852eb7");

    @Test
    void opensUpdatesAndClosesAServerAuthoritativeSession() {
        LinkedAdapter serverAdapter = new LinkedAdapter(Environment.DEDICATED_SERVER);
        LinkedAdapter clientAdapter = new LinkedAdapter(Environment.CLIENT);
        serverAdapter.peer = clientAdapter;
        clientAdapter.peer = serverAdapter;
        DefaultMenuManager server = manager(serverAdapter);
        DefaultMenuManager client = manager(clientAdapter);
        MenuSpec spec = MenuSpec.builder("Test {state}")
                .button(MenuButton.of("confirm", "Confirm", 20, 60, 80))
                .build();
        MenuRef serverRef = server.register("test", spec, action -> action.update(
                MenuState.builder().value("state", "confirmed").build()));
        client.register("test", spec, ignored -> { });

        server.open(PLAYER, serverRef, MenuState.builder().value("state", "ready").build());

        assertNotNull(clientAdapter.shown);
        assertEquals("ready", clientAdapter.shown.state().value("state"));
        assertTrue(server.isOpen(PLAYER, serverRef));

        clientAdapter.actionSender.accept("confirm");

        assertEquals("confirmed", clientAdapter.updated.value("state"));
        clientAdapter.closeSender.run();
        assertFalse(server.isOpen(PLAYER, serverRef));
    }

    private static DefaultMenuManager manager(LinkedAdapter adapter) {
        return new DefaultMenuManager("test_mod", "1.21.4-fabric", adapter,
                new RegistrationGate(), new SystemModLogger("test_mod"),
                new DefaultEventBus(new SystemModLogger("test_mod")));
    }

    private static final class LinkedAdapter implements PlatformAdapter {
        private final Environment environment;
        private LinkedAdapter peer;
        private PayloadReceiver receiver;
        private PortableMenuView shown;
        private MenuState updated;
        private Consumer<String> actionSender;
        private Runnable closeSender;

        private LinkedAdapter(Environment environment) {
            this.environment = environment;
        }

        @Override public PlatformInfo platformInfo() { return new TestPlatformInfo(environment); }
        @Override public CapabilitySet capabilities() { return new ImmutableCapabilitySet(java.util.Set.of()); }
        @Override public Path commonConfigDirectory() { return Path.of("config"); }
        @Override public Path serverConfigDirectory() { return Path.of("serverconfig"); }
        @Override public void registerItem(ResourceId id, ItemSpec spec) { }
        @Override public void registerBlock(ResourceId id, BlockSpec spec, ItemSpec blockItemSpec) { }
        @Override public void registerCreativeTab(ResourceId id, CreativeTabSpec spec) { }
        @Override public void registerCommand(CommandSpec command) { }
        @Override public void registerPayload(ResourceId id, PacketDirection direction, int maximumBytes,
                                              PayloadReceiver receiver) { this.receiver = receiver; }
        @Override public void sendToServer(ResourceId id, byte[] payload) {
            peer.receiver.receive(payload, PacketDirection.SERVERBOUND, Optional.of(PLAYER),
                    reason -> { throw new AssertionError(reason); });
        }
        @Override public void sendToPlayer(UUID playerId, ResourceId id, byte[] payload) {
            peer.receiver.receive(payload, PacketDirection.CLIENTBOUND, Optional.empty(),
                    reason -> { throw new AssertionError(reason); });
        }
        @Override public void sendToAll(ResourceId id, byte[] payload) { }
        @Override public void showMenu(PortableMenuView view, Consumer<String> actionSender, Runnable closeSender) {
            shown = view;
            this.actionSender = actionSender;
            this.closeSender = closeSender;
        }
        @Override public void updateMenu(long sessionId, MenuState state) { updated = state; }
        @Override public void closeMenu(long sessionId) { }
    }

    private record TestPlatformInfo(Environment environment) implements PlatformInfo {
        @Override public Loader loader() { return Loader.FABRIC; }
        @Override public MinecraftVersion minecraftVersion() { return new MinecraftVersion("1.21.4"); }
        @Override public boolean isModLoaded(String modId) { return true; }
        @Override public Optional<String> modVersion(String modId) { return Optional.empty(); }
    }
}
