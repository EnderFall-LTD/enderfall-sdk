package uk.co.enderfall.sdk.runtime.fabric.v1_21_4;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import uk.co.enderfall.sdk.api.event.LifecycleEvent;
import uk.co.enderfall.sdk.api.event.SdkEvents;
import uk.co.enderfall.sdk.api.event.TickEvent;
import uk.co.enderfall.sdk.api.network.PacketDirection;
import uk.co.enderfall.sdk.runtime.PayloadReceiver;
import uk.co.enderfall.sdk.runtime.RuntimeModContext;

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
        ClientTickEvents.END_CLIENT_TICK.register(client -> context.runtimeEvents().publish(
                SdkEvents.TICK, new TickEvent(TickEvent.Side.CLIENT, TickEvent.Phase.END,
                        tick.getAndIncrement())));
    }

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
}
