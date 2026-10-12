package uk.co.enderfall.sdk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
import uk.co.enderfall.sdk.api.ui.MenuTextInput;
import uk.co.enderfall.sdk.api.ui.MenuSelectionEntry;
import uk.co.enderfall.sdk.api.ui.MenuSelectionList;

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
                .textInput(MenuTextInput.multiline("letter.text", "Write...", 20, 30, 140, 24, 64, 2))
                .button(MenuButton.of("confirm", "Confirm", 20, 60, 80))
                .build();
        MenuRef serverRef = server.register("test", spec, action -> action.update(MenuState.builder()
                .value("state", "confirmed")
                .value("received", action.input("letter.text"))
                .build()));
        client.register("test", spec, ignored -> { });

        server.open(PLAYER, serverRef, MenuState.builder().value("state", "ready").build());

        assertNotNull(clientAdapter.shown);
        assertEquals("ready", clientAdapter.shown.state().value("state"));
        assertTrue(server.isOpen(PLAYER, serverRef));

        clientAdapter.actionSender.accept(new PortableMenuSubmission(
                "confirm", Map.of("letter.text", "Hello, world!")));

        assertEquals("confirmed", clientAdapter.updated.value("state"));
        assertEquals("Hello, world!", clientAdapter.updated.value("received"));
        clientAdapter.closeSender.run();
        assertFalse(server.isOpen(PLAYER, serverRef));
    }

    @Test
    void rejectsMissingUnknownAndOversizedSubmittedInputsBeforeTheHandler() {
        LinkedAdapter serverAdapter = new LinkedAdapter(Environment.DEDICATED_SERVER);
        LinkedAdapter clientAdapter = new LinkedAdapter(Environment.CLIENT);
        serverAdapter.peer = clientAdapter;
        clientAdapter.peer = serverAdapter;
        DefaultMenuManager server = manager(serverAdapter);
        DefaultMenuManager client = manager(clientAdapter);
        MenuSpec spec = MenuSpec.builder("Editor")
                .textInput(MenuTextInput.singleLine("name", "Name", 10, 30, 100, 2))
                .button(MenuButton.of("save", "Save", 10, 60, 60))
                .build();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        MenuRef ref = server.register("editor", spec, ignored -> calls.incrementAndGet());
        client.register("editor", spec, ignored -> { });
        assertThrows(IllegalArgumentException.class, () -> server.open(PLAYER, ref,
                MenuState.builder().value("name", "too long").build()));
        server.open(PLAYER, ref, MenuState.empty());

        assertThrows(AssertionError.class, () -> clientAdapter.actionSender.accept(
                new PortableMenuSubmission("save", Map.of())));
        assertThrows(AssertionError.class, () -> clientAdapter.actionSender.accept(
                new PortableMenuSubmission("save", Map.of("other", "ok"))));
        assertThrows(AssertionError.class, () -> clientAdapter.actionSender.accept(
                new PortableMenuSubmission("save", Map.of("name", "\ud83d\ude00\ud83d\ude00\ud83d\ude00"))));
        assertEquals(0, calls.get());

        clientAdapter.actionSender.accept(new PortableMenuSubmission("save", Map.of("name", "ok")));
        assertEquals(1, calls.get());
    }

    @Test
    void validatesSelectionRowsAgainstTheActiveServerState() {
        LinkedAdapter serverAdapter = new LinkedAdapter(Environment.DEDICATED_SERVER);
        LinkedAdapter clientAdapter = new LinkedAdapter(Environment.CLIENT);
        serverAdapter.peer = clientAdapter;
        clientAdapter.peer = serverAdapter;
        DefaultMenuManager server = manager(serverAdapter);
        DefaultMenuManager client = manager(clientAdapter);
        var list = MenuSelectionList.of("recipes", 10, 20, 120, 3);
        var spec = MenuSpec.builder("Recipes").size(160, 120).selectionList(list).build();
        var received = new java.util.concurrent.atomic.AtomicReference<uk.co.enderfall.sdk.api.ui.MenuListAction>();
        var ref = server.register("recipes", spec, action -> received.set(list.action(action).orElseThrow()));
        client.register("recipes", spec, ignored -> { });
        server.open(PLAYER, ref, MenuState.builder().selectionPage(list, java.util.List.of(
                MenuSelectionEntry.enabled("test:first", "First recipe"),
                new MenuSelectionEntry("test:locked", "Locked recipe", false)), 0, "").build());

        assertThrows(AssertionError.class, () -> clientAdapter.actionSender.accept(
                new PortableMenuSubmission("recipes.select.1", Map.of())));
        assertThrows(AssertionError.class, () -> clientAdapter.actionSender.accept(
                new PortableMenuSubmission("recipes.select.2", Map.of())));
        org.junit.jupiter.api.Assertions.assertNull(received.get());

        clientAdapter.actionSender.accept(new PortableMenuSubmission("recipes.select.0", Map.of()));
        assertEquals("test:first", received.get().entryId());
        assertEquals(uk.co.enderfall.sdk.api.ui.MenuListAction.Type.SELECT, received.get().type());
    }

    @Test
    void aFailingConsumerActionHandlerIsLoggedInsteadOfDisconnectingThePlayer() {
        LinkedAdapter serverAdapter = new LinkedAdapter(Environment.DEDICATED_SERVER);
        LinkedAdapter clientAdapter = new LinkedAdapter(Environment.CLIENT);
        serverAdapter.peer = clientAdapter;
        clientAdapter.peer = serverAdapter;
        RecordingLogger logger = new RecordingLogger();
        DefaultMenuManager server = manager(serverAdapter, logger);
        DefaultMenuManager client = manager(clientAdapter, new RecordingLogger());
        MenuSpec spec = MenuSpec.builder("Editor")
                .textInput(MenuTextInput.singleLine("name", "Name", 10, 30, 100, 2))
                .button(MenuButton.of("save", "Save", 10, 60, 60))
                .build();
        // A consumer validating its own input would naturally throw IllegalArgumentException, which
        // used to be reported as a malformed packet and disconnect the player with that message.
        MenuRef ref = server.register("editor", spec, context -> {
            throw new IllegalArgumentException("name " + context.inputs().get("name") + " is reserved");
        });
        client.register("editor", spec, ignored -> { });
        server.open(PLAYER, ref, MenuState.empty());

        // LinkedAdapter turns any disconnect into an AssertionError, so reaching the assertion
        // below proves the player was not kicked and the consumer message never left the server.
        clientAdapter.actionSender.accept(new PortableMenuSubmission("save", Map.of("name", "ok")));

        assertEquals(1, logger.failures().size());
        assertEquals(IllegalArgumentException.class, logger.failures().get(0).getClass());
    }

    // Selection rows resolve entry IDs from the server's copy of the state, so in each test below a
    // click on row 0 must still reach the entry the player can see, not one from an unsent page.

    @Test
    void anUpdateTooLargeToSendLeavesTheServerSessionOnTheStateTheClientShows() {
        SelectionMenu menu = new SelectionMenu((self, action) -> { });

        var failure = assertThrows(IllegalArgumentException.class,
                () -> menu.server.update(PLAYER, oversized(menu.page("test:never_sent"))));
        assertTrue(failure.getMessage().contains(String.valueOf(DefaultMenuManager.PACKET_LIMIT)), failure::getMessage);

        menu.clickFirstRow();
        assertEquals(List.of("test:shown"), menu.received);
        org.junit.jupiter.api.Assertions.assertNull(menu.clientAdapter.updated);
    }

    @Test
    void aHandlerUpdateTooLargeToSendLeavesTheServerSessionOnTheStateTheClientShows() {
        // A paging handler whose next page is too large: its update fails and is logged.
        SelectionMenu menu = new SelectionMenu((self, action) -> action.update(oversized(self.page("test:never_sent"))));

        menu.clickFirstRow();
        menu.clickFirstRow();

        assertEquals(List.of("test:shown", "test:shown"), menu.received);
        assertEquals(2, menu.serverLogger.failures().size());
        org.junit.jupiter.api.Assertions.assertNull(menu.clientAdapter.updated);
    }

    @Test
    void anUpdateTheTransportFailsToDeliverIsWithdrawnFromTheServerSession() {
        SelectionMenu menu = new SelectionMenu((self, action) -> { });

        menu.serverAdapter.failSends = true;
        var failure = assertThrows(IllegalStateException.class,
                () -> menu.server.update(PLAYER, menu.page("test:never_sent")));
        assertEquals("transport closed", failure.getMessage());
        menu.serverAdapter.failSends = false;

        menu.clickFirstRow();
        assertEquals(List.of("test:shown"), menu.received);
    }

    @Test
    void aPeerOnTheSixtyFourEntryProtocolIsRejectedByVersionNotByEntryCount() {
        // 0.1.0-beta.1 spoke menu protocol 2 and rejects states above 64 entries. The 128-entry
        // budget is a wire change, so a mixed pair must fail on the first menu packet with a clear
        // reason, not only when a menu happens to carry more than 64 entries.
        LinkedAdapter clientAdapter = new LinkedAdapter(Environment.CLIENT);
        manager(clientAdapter);
        var writer = new uk.co.enderfall.sdk.runtime.network.ByteArrayPacketWriter(DefaultMenuManager.PACKET_LIMIT);
        writer.writeVarInt(2);
        writer.writeByte(1);
        var reasons = new java.util.ArrayList<String>();

        clientAdapter.receiver.receive(writer.toByteArray(), PacketDirection.CLIENTBOUND, Optional.empty(),
                reasons::add);

        assertEquals(1, reasons.size());
        assertTrue(reasons.get(0).contains("Unsupported EnderFall menu protocol 2"), reasons::toString);
    }

    /** A linked server and client with a three-row selection menu open on entry "test:shown". */
    private static final class SelectionMenu {
        private final MenuSelectionList list = MenuSelectionList.of("recipes", 10, 20, 120, 3);
        private final LinkedAdapter serverAdapter = new LinkedAdapter(Environment.DEDICATED_SERVER);
        private final LinkedAdapter clientAdapter = new LinkedAdapter(Environment.CLIENT);
        private final RecordingLogger serverLogger = new RecordingLogger();
        private final List<String> received = new java.util.ArrayList<>();
        private final DefaultMenuManager server;

        private SelectionMenu(
                java.util.function.BiConsumer<SelectionMenu, uk.co.enderfall.sdk.api.ui.MenuActionContext> afterSelect) {
            serverAdapter.peer = clientAdapter;
            clientAdapter.peer = serverAdapter;
            server = manager(serverAdapter, serverLogger);
            DefaultMenuManager client = manager(clientAdapter);
            var spec = MenuSpec.builder("Recipes").size(160, 120).selectionList(list).build();
            var ref = server.register("recipes", spec, action -> {
                received.add(list.action(action).orElseThrow().entryId());
                afterSelect.accept(this, action);
            });
            client.register("recipes", spec, ignored -> { });
            server.open(PLAYER, ref, page("test:shown"));
        }

        private MenuState page(String firstEntry) {
            return MenuState.builder().selectionPage(list,
                    List.of(MenuSelectionEntry.enabled(firstEntry, "Entry")), 0, "").build();
        }

        private void clickFirstRow() {
            clientAdapter.actionSender.accept(new PortableMenuSubmission("recipes.select.0", Map.of()));
        }
    }

    /** Adds legal values until the state no longer fits one menu packet. */
    private static MenuState oversized(MenuState base) {
        MenuState.Builder builder = MenuState.builder();
        base.values().forEach(builder::value);
        for (int index = 0; index < 20; index++) {
            builder.value("filler." + index, "x".repeat(1_000));
        }
        return builder.build();
    }

    private static DefaultMenuManager manager(LinkedAdapter adapter) {
        return manager(adapter, new RecordingLogger());
    }

    private static DefaultMenuManager manager(LinkedAdapter adapter, RecordingLogger logger) {
        return new DefaultMenuManager("test_mod", "1.21.4-fabric", adapter,
                new RegistrationGate(), logger, new DefaultEventBus(logger));
    }

    private static final class LinkedAdapter implements PlatformAdapter {
        private final Environment environment;
        private LinkedAdapter peer;
        private PayloadReceiver receiver;
        private PortableMenuView shown;
        private MenuState updated;
        private Consumer<PortableMenuSubmission> actionSender;
        private Runnable closeSender;
        private boolean failSends;

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
            if (failSends) throw new IllegalStateException("transport closed");
            peer.receiver.receive(payload, PacketDirection.CLIENTBOUND, Optional.empty(),
                    reason -> { throw new AssertionError(reason); });
        }
        @Override public void sendToAll(ResourceId id, byte[] payload) { }
        @Override public void showMenuWithInputs(PortableMenuView view,
                Consumer<PortableMenuSubmission> actionSender, Runnable closeSender) {
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
