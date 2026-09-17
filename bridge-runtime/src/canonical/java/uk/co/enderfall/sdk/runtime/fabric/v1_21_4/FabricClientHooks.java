package uk.co.enderfall.sdk.runtime.fabric.v1_21_4;

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
import uk.co.enderfall.sdk.api.ui.MenuState;

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

    static void registerWorkbench(MenuType<FabricWorkbenchMenu> menuType) {
        MenuScreens.register(menuType, FabricWorkbenchScreen::new);
    }

    static void showMenu(PortableMenuView view, Consumer<String> actionSender, Runnable closeSender) {
        FabricPortableMenuScreen.show(view, actionSender, closeSender);
    }

    static void updateMenu(long sessionId, MenuState state) {
        FabricPortableMenuScreen.update(sessionId, state);
    }

    static void closeMenu(long sessionId) {
        FabricPortableMenuScreen.close(sessionId);
    }
}
