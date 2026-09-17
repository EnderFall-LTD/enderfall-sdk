package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.MenuAbi;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Shared modern FML client lifecycle, tick, screen, and payload hooks. */
final class ClientHooksEmitter {
    private ClientHooksEmitter() { }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> paths) throws BridgeGenerationException {
        if (!TargetCatalog.standard().require(target.id()).equals(target)) throw new BridgeGenerationException("Unreviewed client-hooks target " + target.id());
        if (target.loaderAbi() != LoaderAbi.MODERN_NEOFORGE) return List.of();
        String root = "uk/co/enderfall/sdk/runtime/neoforge/v1_21_4/";
        String canonical = root + "NeoForgeClientHooks.java";
        if (!paths.contains(canonical)) return List.of();
        if (!paths.containsAll(Set.of(root + "NeoForgeWorkbenchBinding.java", root + "NeoForgeWorkbenchScreen.java",
                root + "NeoForgeRawPayload.java", root + "NeoForgePortableMenuScreen.java"))) {
            throw new BridgeGenerationException(target.id() + " requires client hook screen, binding, and payload declarations");
        }
        boolean extracted = target.menuAbi() == MenuAbi.V26_2;
        String setup = extracted
                ? "        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {\n            context.runtimeEvents().publish(\n                    SdkEvents.LIFECYCLE, new LifecycleEvent(LifecycleEvent.Stage.CLIENT_STARTED));\n        }));\n"
                : "        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> context.runtimeEvents().publish(\n                SdkEvents.LIFECYCLE, new LifecycleEvent(LifecycleEvent.Stage.CLIENT_STARTED))));\n";
        String content = HOOKS.formatted(
                extracted ? "import net.neoforged.neoforge.client.network.ClientPacketDistributor;\n" : "",
                extracted ? "" : "import net.neoforged.neoforge.network.PacketDistributor;\n",
                extracted ? "NeoForge26" : "NeoForge", setup,
                extracted ? "ClientPacketDistributor" : "PacketDistributor");
        return List.of(new RuntimeSource(canonical, root + (extracted ? "NeoForge26" : "NeoForge") + "ClientHooks.java",
                content.getBytes(StandardCharsets.UTF_8)));
    }

    // Preserve original native method layout and event ordering for source/class parity.
    private static final String HOOKS = """
            package uk.co.enderfall.sdk.runtime.neoforge.v1_21_4;
            
            import java.util.Collection;
            import java.util.concurrent.atomic.AtomicLong;
            import java.util.function.Consumer;
            import net.minecraft.client.Minecraft;
            import net.neoforged.bus.api.IEventBus;
            import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
            import net.neoforged.neoforge.client.event.ClientTickEvent;
            import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
            %1$simport net.neoforged.neoforge.common.NeoForge;
            import net.neoforged.neoforge.event.GameShuttingDownEvent;
            %2$simport uk.co.enderfall.sdk.api.event.LifecycleEvent;
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
                                    Collection<%3$sWorkbenchBinding> workbenches) {
                    AtomicLong tick = new AtomicLong();
                    modBus.addListener((RegisterMenuScreensEvent event) -> workbenches.forEach(binding ->
                            event.register(binding.menuType().get(), %3$sWorkbenchScreen::new)));
            %4$s        NeoForge.EVENT_BUS.addListener((GameShuttingDownEvent event) -> context.runtimeEvents().publish(
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
                    %5$s.sendToServer(payload);
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
            """;
}
