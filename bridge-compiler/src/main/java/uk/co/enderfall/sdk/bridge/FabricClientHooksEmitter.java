package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Shared client hooks with explicit native transport policies. */
final class FabricClientHooksEmitter {
    private FabricClientHooksEmitter() { }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> paths) throws BridgeGenerationException {
        if (!TargetCatalog.standard().require(target.id()).equals(target)) throw new BridgeGenerationException("Unreviewed client-hooks target " + target.id());
        if (target.loaderAbi() != LoaderAbi.FABRIC) return List.of();
        String root = "uk/co/enderfall/sdk/runtime/fabric/v1_21_4/";
        String canonical = root + "FabricClientHooks.java";
        if (!paths.contains(canonical)) return List.of();
        boolean legacy = target.minecraftVersion() == uk.co.enderfall.sdk.bridge.model.MinecraftVersion.V1_20_1;
        if (!paths.containsAll(Set.of(root + "FabricWorkbenchMenu.java", root + "FabricWorkbenchScreen.java",
                root + "FabricPortableMenuScreen.java")) || (!legacy && !paths.contains(root + "FabricRawPayload.java"))) {
            throw new BridgeGenerationException(target.id() + " requires client hook menu, screen, and payload declarations");
        }
        String content = HOOKS.formatted((legacy ? LEGACY_IMPORTS : MODERN_IMPORTS).stripTrailing() + "\n",
                legacy ? LEGACY_TRANSPORT : MODERN_TRANSPORT, legacy ? "Fabric1201" : "Fabric");
        return List.of(new RuntimeSource(canonical, root + (legacy ? "Fabric1201" : "Fabric") + "ClientHooks.java",
                content.getBytes(StandardCharsets.UTF_8)));
    }

    private static final String MODERN_IMPORTS = """
            import java.util.Optional;
            import java.util.concurrent.atomic.AtomicLong;
            import java.util.function.Consumer;
            import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
            import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
            import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
            import net.minecraft.client.gui.screens.MenuScreens;
            import net.minecraft.network.chat.Component;
            import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
            import net.minecraft.world.inventory.MenuType;
            import uk.co.enderfall.sdk.api.event.LifecycleEvent;
            import uk.co.enderfall.sdk.api.event.SdkEvents;
            import uk.co.enderfall.sdk.api.event.TickEvent;
            import uk.co.enderfall.sdk.api.network.PacketDirection;
            import uk.co.enderfall.sdk.runtime.IntegrationTestControl;
            import uk.co.enderfall.sdk.runtime.PayloadReceiver;
            import uk.co.enderfall.sdk.runtime.RuntimeModContext;
            import uk.co.enderfall.sdk.runtime.PortableMenuView;
            import uk.co.enderfall.sdk.runtime.PortableMenuSubmission;
            import uk.co.enderfall.sdk.api.ui.MenuState;
            
            """;
    private static final String LEGACY_IMPORTS = """
            import java.util.Optional;
            import java.util.concurrent.atomic.AtomicLong;
            import java.util.function.Consumer;
            import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
            import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
            import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
            import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
            import net.minecraft.network.chat.Component;
            import net.minecraft.client.gui.screens.MenuScreens;
            import net.minecraft.world.inventory.MenuType;
            import net.minecraft.resources.ResourceLocation;
            import uk.co.enderfall.sdk.api.event.LifecycleEvent;
            import uk.co.enderfall.sdk.api.event.SdkEvents;
            import uk.co.enderfall.sdk.api.event.TickEvent;
            import uk.co.enderfall.sdk.api.network.PacketDirection;
            import uk.co.enderfall.sdk.runtime.IntegrationTestControl;
            import uk.co.enderfall.sdk.runtime.PayloadReceiver;
            import uk.co.enderfall.sdk.runtime.PortableMenuView;
            import uk.co.enderfall.sdk.runtime.PortableMenuSubmission;
            import uk.co.enderfall.sdk.runtime.RuntimeModContext;
            import uk.co.enderfall.sdk.api.ui.MenuState;
            
            """;
    private static final String MODERN_TRANSPORT = """
                static void registerReceiver(CustomPacketPayload.Type<FabricRawPayload> type, PayloadReceiver receiver) {
                    if (!ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> receiver.receive(
                            payload.bytes(), PacketDirection.CLIENTBOUND, Optional.empty(), reason -> {
                                if (context.client().getConnection() != null) {
                                    context.client().getConnection().getConnection().disconnect(Component.literal(reason));
                                }
                            }))) {
                        throw new IllegalStateException("Duplicate clientbound payload " + type.id());
                    }
                }
            
                static void sendToServer(FabricRawPayload payload) {
                    ClientPlayNetworking.send(payload);
                }
            
            """;
    private static final String LEGACY_TRANSPORT = """
                static void registerReceiver(ResourceLocation channel, int maximumBytes, PayloadReceiver receiver) {
                    if (!ClientPlayNetworking.registerGlobalReceiver(channel, (client, handler, buffer, sender) -> {
                        int length = buffer.readableBytes();
                        if (length > maximumBytes) {
                            if (client.getConnection() != null) {
                                client.getConnection().getConnection().disconnect(
                                        Component.literal("Payload " + channel + " exceeds " + maximumBytes + " bytes"));
                            }
                            return;
                        }
                        byte[] bytes = new byte[length];
                        buffer.readBytes(bytes);
                        client.execute(() -> receiver.receive(bytes, PacketDirection.CLIENTBOUND, Optional.empty(), reason -> {
                            if (client.getConnection() != null) {
                                client.getConnection().getConnection().disconnect(Component.literal(reason));
                            }
                        }));
                    })) {
                        throw new IllegalStateException("Duplicate clientbound payload " + channel);
                    }
                }
            
                static void sendToServer(ResourceLocation channel, byte[] payload) {
                    var buffer = PacketByteBufs.create();
                    buffer.writeBytes(payload);
                    ClientPlayNetworking.send(channel, buffer);
                }
            
            """;
    private static final String HOOKS = """
            package uk.co.enderfall.sdk.runtime.fabric.v1_21_4;
            
            %1$s
            final class FabricClientHooks {
                private FabricClientHooks() {
                }
            
                static void installLifecycle(RuntimeModContext context) {
                    AtomicLong tick = new AtomicLong();
                    ClientLifecycleEvents.CLIENT_STARTED.register(client -> context.runtimeEvents().publish(
                            SdkEvents.LIFECYCLE, new LifecycleEvent(LifecycleEvent.Stage.CLIENT_STARTED)));
                    ClientLifecycleEvents.CLIENT_STOPPING.register(client -> context.runtimeEvents().publish(
                            SdkEvents.LIFECYCLE, new LifecycleEvent(LifecycleEvent.Stage.CLIENT_STOPPING)));
                    ClientTickEvents.START_CLIENT_TICK.register(client -> context.runtimeEvents().publish(
                            SdkEvents.TICK, new TickEvent(TickEvent.Side.CLIENT, TickEvent.Phase.START, tick.get())));
                    ClientTickEvents.END_CLIENT_TICK.register(client -> {
                        context.runtimeEvents().publish(SdkEvents.TICK,
                                new TickEvent(TickEvent.Side.CLIENT, TickEvent.Phase.END, tick.getAndIncrement()));
                        if (IntegrationTestControl.shouldStopSmokeClient()) {
                            client.stop();
                        }
                    });
                }
            
            %2$s    static void registerWorkbench(MenuType<%3$sWorkbenchMenu> menuType) {
                    MenuScreens.register(menuType, %3$sWorkbenchScreen::new);
                }
            
                static void showMenu(PortableMenuView view, Consumer<PortableMenuSubmission> actionSender,
                                     Runnable closeSender) {
                    FabricPortableMenuScreen.show(view, actionSender, closeSender);
                }
            
                static void updateMenu(long sessionId, MenuState state) {
                    FabricPortableMenuScreen.update(sessionId, state);
                }
            
                static void closeMenu(long sessionId) {
                    FabricPortableMenuScreen.close(sessionId);
                }
            }
            """;
}
