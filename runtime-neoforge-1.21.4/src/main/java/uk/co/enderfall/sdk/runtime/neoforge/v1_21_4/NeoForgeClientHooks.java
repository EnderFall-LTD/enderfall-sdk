package uk.co.enderfall.sdk.runtime.neoforge.v1_21_4;

import java.util.concurrent.atomic.AtomicLong;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import uk.co.enderfall.sdk.api.event.LifecycleEvent;
import uk.co.enderfall.sdk.api.event.SdkEvents;
import uk.co.enderfall.sdk.api.event.TickEvent;
import uk.co.enderfall.sdk.runtime.RuntimeModContext;

final class NeoForgeClientHooks {
    private NeoForgeClientHooks() {
    }

    static void install(IEventBus modBus, RuntimeModContext context) {
        AtomicLong tick = new AtomicLong();
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> context.runtimeEvents().publish(
                SdkEvents.LIFECYCLE, new LifecycleEvent(LifecycleEvent.Stage.CLIENT_STARTED))));
        NeoForge.EVENT_BUS.addListener((GameShuttingDownEvent event) -> context.runtimeEvents().publish(
                SdkEvents.LIFECYCLE, new LifecycleEvent(LifecycleEvent.Stage.CLIENT_STOPPING)));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre event) -> context.runtimeEvents().publish(
                SdkEvents.TICK, new TickEvent(TickEvent.Side.CLIENT, TickEvent.Phase.START, tick.get())));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> context.runtimeEvents().publish(
                SdkEvents.TICK, new TickEvent(TickEvent.Side.CLIENT, TickEvent.Phase.END, tick.getAndIncrement())));
    }

    static void sendToServer(NeoForgeRawPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
