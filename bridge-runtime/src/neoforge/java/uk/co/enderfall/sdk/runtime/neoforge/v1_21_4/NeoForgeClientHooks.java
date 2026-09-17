package uk.co.enderfall.sdk.runtime.neoforge.v1_21_4;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import uk.co.enderfall.sdk.api.event.LifecycleEvent;
import uk.co.enderfall.sdk.api.event.SdkEvents;
import uk.co.enderfall.sdk.api.event.TickEvent;
import uk.co.enderfall.sdk.runtime.IntegrationTestControl;
import uk.co.enderfall.sdk.runtime.RuntimeModContext;
import uk.co.enderfall.sdk.runtime.PortableMenuView;
import uk.co.enderfall.sdk.api.ui.MenuState;

final class NeoForgeClientHooks {
    private NeoForgeClientHooks() {
    }

    static void install(IEventBus modBus, RuntimeModContext context,
                        Collection<NeoForgeWorkbenchBinding> workbenches) {
        AtomicLong tick = new AtomicLong();
        modBus.addListener((RegisterMenuScreensEvent event) -> workbenches.forEach(binding ->
                event.register(binding.menuType().get(), NeoForgeWorkbenchScreen::new)));
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> context.runtimeEvents().publish(
                SdkEvents.LIFECYCLE, new LifecycleEvent(LifecycleEvent.Stage.CLIENT_STARTED))));
        NeoForge.EVENT_BUS.addListener((GameShuttingDownEvent event) -> context.runtimeEvents().publish(
                SdkEvents.LIFECYCLE, new LifecycleEvent(LifecycleEvent.Stage.CLIENT_STOPPING)));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre event) -> context.runtimeEvents().publish(
                SdkEvents.TICK, new TickEvent(TickEvent.Side.CLIENT, TickEvent.Phase.START, tick.get())));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            context.runtimeEvents().publish(SdkEvents.TICK,
                    new TickEvent(TickEvent.Side.CLIENT, TickEvent.Phase.END, tick.getAndIncrement()));
            if (IntegrationTestControl.shouldStopSmokeClient()) {
                Minecraft.getInstance().stop();
            }
        });
    }

    static void sendToServer(NeoForgeRawPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    static void showMenu(PortableMenuView view, Consumer<String> actionSender, Runnable closeSender) {
        NeoForgePortableMenuScreen.show(view, actionSender, closeSender);
    }

    static void updateMenu(long sessionId, MenuState state) {
        NeoForgePortableMenuScreen.update(sessionId, state);
    }

    static void closeMenu(long sessionId) {
        NeoForgePortableMenuScreen.close(sessionId);
    }
}
