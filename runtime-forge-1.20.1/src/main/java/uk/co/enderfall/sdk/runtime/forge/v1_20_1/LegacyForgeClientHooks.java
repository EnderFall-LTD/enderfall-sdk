package uk.co.enderfall.sdk.runtime.forge.v1_20_1;

import io.netty.buffer.Unpooled;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import uk.co.enderfall.sdk.api.event.LifecycleEvent;
import uk.co.enderfall.sdk.api.event.SdkEvents;
import uk.co.enderfall.sdk.runtime.RuntimeModContext;

final class LegacyForgeClientHooks {
    private LegacyForgeClientHooks() {
    }

    static void install(IEventBus modBus, RuntimeModContext context) {
        AtomicLong tick = new AtomicLong();
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> context.runtimeEvents().publish(
                SdkEvents.LIFECYCLE, new LifecycleEvent(LifecycleEvent.Stage.CLIENT_STARTED))));
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
                tick.incrementAndGet();
            }
        });
    }

    static void sendToServer(ResourceLocation channel, byte[] payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            throw new IllegalStateException("No Minecraft server connection is active");
        }
        connection.send(new ServerboundCustomPayloadPacket(channel,
                new FriendlyByteBuf(Unpooled.wrappedBuffer(payload))));
    }
}
