package uk.co.enderfall.sdk.testmod;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import uk.co.enderfall.sdk.api.ClientModContext;
import uk.co.enderfall.sdk.api.EnderfallClientMod;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.config.ConfigScope;
import uk.co.enderfall.sdk.api.config.ConfigSpec;
import uk.co.enderfall.sdk.api.event.LifecycleEvent;
import uk.co.enderfall.sdk.api.event.SdkEvents;
import uk.co.enderfall.sdk.api.event.TickEvent;
import uk.co.enderfall.sdk.api.client.ui.ClientScreenRef;
import uk.co.enderfall.sdk.api.client.ui.ClientScreenSpec;
import uk.co.enderfall.sdk.api.client.ui.PortableClientScreen;
import uk.co.enderfall.sdk.api.client.ui.UiRect;
import uk.co.enderfall.sdk.api.client.ui.UiRenderContext;
import uk.co.enderfall.sdk.api.client.ui.UiTextAlign;
import uk.co.enderfall.sdk.api.item.ItemStackRef;
import uk.co.enderfall.sdk.api.platform.Capability;
import uk.co.enderfall.sdk.api.registry.ItemRef;

public final class ContractTestClient implements EnderfallClientMod {
    private static final String CLIENT_COMPLETE_PROPERTY =
            "uk.co.enderfall.sdk.test.clientSmokeComplete";

    @Override
    public void initialize(ClientModContext context) {
        boolean smokeMode = "true".equalsIgnoreCase(System.getenv("ENDERFALL_CLIENT_SMOKE"));
        boolean connectionMode = "true".equalsIgnoreCase(System.getenv("ENDERFALL_CONNECTION_SMOKE"));
        if (smokeMode) {
            System.clearProperty(CLIENT_COMPLETE_PROPERTY);
        }
        AtomicBoolean clientStarted = new AtomicBoolean();
        AtomicBoolean screenRendered = new AtomicBoolean();
        AtomicBoolean smokeCompleted = new AtomicBoolean();
        AtomicInteger smokeTicks = new AtomicInteger();
        ClientScreenRef smokeScreen = context.capabilities().supports(Capability.GENERAL_CLIENT_SCREENS)
                ? context.screens().register(context.id("client_contract"),
                        ClientScreenSpec.of("EnderFall Client Contract"),
                        () -> new ContractScreen(screenRendered))
                : null;
        ConfigSpec.Builder clientConfig = ConfigSpec.builder();
        clientConfig.booleanValue("show_diagnostics", true, "Show client contract diagnostics.");
        context.configs().register("client", ConfigScope.CLIENT, clientConfig.build());
        context.events().subscribe(SdkEvents.LIFECYCLE, event -> {
            if (event.stage() == LifecycleEvent.Stage.CLIENT_STARTED) {
                context.logger().info("ENDERFALL_CLIENT_SMOKE_READY {}", context.platform().targetId());
                clientStarted.set(true);
                if (smokeMode && smokeScreen != null) context.screens().open(smokeScreen);
            }
        });
        context.events().subscribe(SdkEvents.TICK, event -> {
            if (event.side() == TickEvent.Side.CLIENT && event.phase() == TickEvent.Phase.END
                    && event.tick() == 1) {
                context.logger().debug("Client tick {}", event.tick());
            }
            if (!smokeMode || connectionMode || !clientStarted.get()
                    || event.side() != TickEvent.Side.CLIENT || event.phase() != TickEvent.Phase.END) {
                return;
            }
            int elapsedTicks = smokeTicks.incrementAndGet();
            if (smokeScreen != null && !screenRendered.get() && elapsedTicks >= 20 && elapsedTicks % 20 == 0) {
                // Quick-play test worlds can replace a screen opened during CLIENT_STARTED before its first frame.
                context.screens().open(smokeScreen);
            }
            if (elapsedTicks >= 20 && (smokeScreen == null || screenRendered.get())
                    && smokeCompleted.compareAndSet(false, true)) {
                context.logger().info("ENDERFALL_CLIENT_SMOKE_COMPLETE {}", context.platform().targetId());
                System.out.flush();
                System.err.flush();
                System.setProperty(CLIENT_COMPLETE_PROPERTY, "true");
            }
        });
    }

    private static final class ContractScreen implements PortableClientScreen {
        private final AtomicBoolean rendered;

        private ContractScreen(AtomicBoolean rendered) {
            this.rendered = rendered;
        }

        @Override public void render(UiRenderContext graphics) {
            rendered.set(true);
            int panelWidth = 220;
            int panelHeight = 72;
            int left = (graphics.frame().width() - panelWidth) / 2;
            int top = (graphics.frame().height() - panelHeight) / 2;
            graphics.fill(new UiRect(left, top, panelWidth, panelHeight), 0xE815111D);
            graphics.text("EnderFall portable UI", left + panelWidth / 2f, top + 18,
                    0xFFFFFFFF, true, UiTextAlign.CENTER);
            graphics.pushClip(new UiRect(left + 8, top + 34, panelWidth - 16, 18));
            graphics.text("fill + text + clipping + lifecycle", left + panelWidth / 2f, top + 38,
                    0xFFB998FF, false, UiTextAlign.CENTER);
            graphics.popClip();
            ItemStackRef diamond = ItemStackRef.of(new ItemRef(ResourceId.of("minecraft", "diamond")), 12);
            graphics.item(diamond, left + 12, top + 50);
            if (new UiRect(left + 12, top + 50, 16, 16).contains(
                    graphics.frame().mouseX(), graphics.frame().mouseY())) {
                graphics.itemTooltip(diamond, (int) graphics.frame().mouseX(),
                        (int) graphics.frame().mouseY());
            }
        }
    }
}
