package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Shared client hooks with explicit native transport policies. */
final class LegacyClientHooksEmitter {
    private LegacyClientHooksEmitter() { }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> paths) throws BridgeGenerationException {
        if (!TargetCatalog.standard().require(target.id()).equals(target)) throw new BridgeGenerationException("Unreviewed client-hooks target " + target.id());
        if (target.loaderAbi() != LoaderAbi.LEGACY_FML) return List.of();
        String root = "uk/co/enderfall/sdk/runtime/neoforge/v1_21_4/";
        String canonical = root + "NeoForgeClientHooks.java";
        if (!paths.contains(canonical)) return List.of();
        if (!paths.containsAll(Set.of(root + "NeoForgeWorkbenchBinding.java", root + "NeoForgeWorkbenchScreen.java",
                root + "NeoForgePortableMenuScreen.java"))) {
            throw new BridgeGenerationException(target.id() + " requires client hook binding and screen declarations");
        }
        return List.of(new RuntimeSource(canonical,
                "uk/co/enderfall/sdk/runtime/forge/v1_20_1/LegacyForgeClientHooks.java",
                HOOKS.getBytes(StandardCharsets.UTF_8)));
    }

    private static final String HOOKS = """
            package uk.co.enderfall.sdk.runtime.forge.v1_20_1;
            
            import java.net.UnknownHostException;
            import java.util.Collection;
            import java.util.concurrent.atomic.AtomicBoolean;
            import java.util.concurrent.atomic.AtomicLong;
            import java.util.function.Consumer;
            import com.mojang.logging.LogUtils;
            import net.minecraft.client.Minecraft;
            import net.minecraft.client.gui.screens.ConnectScreen;
            import net.minecraft.client.gui.screens.MenuScreens;
            import net.minecraft.client.gui.screens.TitleScreen;
            import net.minecraft.client.multiplayer.ServerData;
            import net.minecraft.client.multiplayer.ServerStatusPinger;
            import net.minecraft.client.multiplayer.resolver.ServerAddress;
            import net.minecraftforge.common.MinecraftForge;
            import net.minecraftforge.event.GameShuttingDownEvent;
            import net.minecraftforge.event.TickEvent;
            import net.minecraftforge.eventbus.api.IEventBus;
            import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
            import net.minecraftforge.network.simple.SimpleChannel;
            import org.slf4j.Logger;
            import uk.co.enderfall.sdk.api.event.LifecycleEvent;
            import uk.co.enderfall.sdk.api.event.SdkEvents;
            import uk.co.enderfall.sdk.runtime.IntegrationTestControl;
            import uk.co.enderfall.sdk.runtime.PortableMenuView;
            import uk.co.enderfall.sdk.runtime.RuntimeModContext;
            import uk.co.enderfall.sdk.api.ui.MenuState;
            
            final class LegacyForgeClientHooks {
                private static final Logger LOGGER = LogUtils.getLogger();
                private static final String SMOKE_LOOPBACK_HOST = "127.0.0.1";
                private static final AtomicBoolean AUTOMATIC_CONNECTION_INSTALLED = new AtomicBoolean();
            
                private LegacyForgeClientHooks() {
                }
            
                static void install(IEventBus modBus, RuntimeModContext context,
                                    Collection<LegacyForgeWorkbenchBinding> workbenches) {
                    AtomicLong tick = new AtomicLong();
                    LegacyNeoForgeAutomaticConnection automaticConnection = automaticConnection(context);
                    modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
                        workbenches.forEach(binding -> MenuScreens.register(
                                binding.menuType().get(), LegacyForgeWorkbenchScreen::new));
                        context.runtimeEvents().publish(
                                SdkEvents.LIFECYCLE, new LifecycleEvent(LifecycleEvent.Stage.CLIENT_STARTED));
                    }));
                    MinecraftForge.EVENT_BUS.addListener((GameShuttingDownEvent event) -> context.runtimeEvents().publish(
                            SdkEvents.LIFECYCLE, new LifecycleEvent(LifecycleEvent.Stage.CLIENT_STOPPING)));
                    MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
                        uk.co.enderfall.sdk.api.event.TickEvent.Phase phase = event.phase == TickEvent.Phase.START
                                ? uk.co.enderfall.sdk.api.event.TickEvent.Phase.START
                                : uk.co.enderfall.sdk.api.event.TickEvent.Phase.END;
                        long current = tick.get();
                        context.runtimeEvents().publish(SdkEvents.TICK,
                                new uk.co.enderfall.sdk.api.event.TickEvent(
                                        uk.co.enderfall.sdk.api.event.TickEvent.Side.CLIENT, phase, current));
                        if (event.phase == TickEvent.Phase.END) {
                            long completedTick = tick.incrementAndGet();
                            if (automaticConnection != null) {
                                automaticConnection.tick(completedTick);
                            }
                            if (IntegrationTestControl.shouldStopSmokeClient()) {
                                Minecraft.getInstance().stop();
                            }
                        }
                    });
                }
            
                static void sendToServer(SimpleChannel channel, byte[] payload) {
                    var connection = Minecraft.getInstance().getConnection();
                    if (connection == null) {
                        throw new IllegalStateException("No Minecraft server connection is active");
                    }
                    if (!channel.isRemotePresent(connection.getConnection())) {
                        throw new IllegalStateException("EnderFall network channel is unavailable on the server");
                    }
                    channel.sendToServer(payload);
                }
            
                static void showMenu(PortableMenuView view, Consumer<String> actionSender, Runnable closeSender) {
                    LegacyForgePortableMenuScreen.show(view, actionSender, closeSender);
                }
            
                static void updateMenu(long sessionId, MenuState state) {
                    LegacyForgePortableMenuScreen.update(sessionId, state);
                }
            
                static void closeMenu(long sessionId) {
                    LegacyForgePortableMenuScreen.close(sessionId);
                }
            
                private static LegacyNeoForgeAutomaticConnection automaticConnection(RuntimeModContext context) {
                    if (!context.platform().targetId().equals("1.20.1-neoforge")
                            || !"true".equalsIgnoreCase(System.getenv("ENDERFALL_CONNECTION_SMOKE"))) {
                        return null;
                    }
                    String host = System.getenv("ENDERFALL_TEST_SERVER_HOST");
                    String portValue = System.getenv("ENDERFALL_TEST_SERVER_PORT");
                    if (host == null || host.isBlank() || portValue == null || portValue.isBlank()
                            || !AUTOMATIC_CONNECTION_INSTALLED.compareAndSet(false, true)) {
                        return null;
                    }
                    try {
                        if (!SMOKE_LOOPBACK_HOST.equals(host)) {
                            throw new IllegalArgumentException("host must be the IPv4 loopback address "
                                    + SMOKE_LOOPBACK_HOST);
                        }
                        int port = Integer.parseInt(portValue);
                        if (port < 1 || port > 65_535) {
                            throw new IllegalArgumentException("port is outside 1-65535");
                        }
                        return new LegacyNeoForgeAutomaticConnection(port);
                    } catch (RuntimeException exception) {
                        throw new IllegalStateException("Invalid EnderFall legacy NeoForge smoke server port: "
                                + portValue, exception);
                    }
                }
            
                /**
                 * NeoForge 47.1.x needs the Forge status-ping data before its login handshake.
                 * Vanilla Quick Play connects without that ping, so the process test performs
                 * the same ping-then-connect sequence as the multiplayer screen.
                 */
                private static final class LegacyNeoForgeAutomaticConnection {
                    private static final long START_TICK = 40L;
                    private final ServerData server;
                    private final ServerAddress address;
                    private final ServerStatusPinger pinger = new ServerStatusPinger();
                    private boolean pingStarted;
                    private boolean connectionStarted;
            
                    private LegacyNeoForgeAutomaticConnection(int port) {
                        String endpoint = SMOKE_LOOPBACK_HOST + ':' + port;
                        server = new ServerData("EnderFall same-loader smoke", endpoint, false);
                        address = new ServerAddress(SMOKE_LOOPBACK_HOST, port);
                        LOGGER.info("ENDERFALL_LEGACY_NEOFORGE_CONNECTOR_READY {}", address);
                    }
            
                    private void tick(long tick) {
                        Minecraft minecraft = Minecraft.getInstance();
                        if (!pingStarted && tick >= START_TICK && minecraft.getConnection() == null) {
                            pingStarted = true;
                            try {
                                // This callback only signals a changed favicon, not a completed ping.
                                pinger.pingServer(server, () -> { });
                            } catch (UnknownHostException exception) {
                                throw new IllegalStateException("Cannot resolve EnderFall legacy NeoForge smoke server "
                                        + server.ip, exception);
                            }
                        }
                        if (pingStarted && !connectionStarted) {
                            pinger.tick();
                        }
                        // Pong is processed after Forge's status data; neither depends on a server icon.
                        if (pingStarted && !connectionStarted && server.ping >= 0L && server.forgeData != null) {
                            connectionStarted = true;
                            pinger.removeAll();
                            LOGGER.info("ENDERFALL_LEGACY_NEOFORGE_PING_READY {}", server.ip);
                            ConnectScreen.startConnecting(new TitleScreen(), minecraft, address, server, true);
                        }
                    }
                }
            }
            """;
}
