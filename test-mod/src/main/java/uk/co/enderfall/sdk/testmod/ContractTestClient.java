package uk.co.enderfall.sdk.testmod;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import uk.co.enderfall.sdk.api.ClientModContext;
import uk.co.enderfall.sdk.api.EnderfallClientMod;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.config.ConfigScope;
import uk.co.enderfall.sdk.api.config.ConfigSpec;
import uk.co.enderfall.sdk.api.command.Arguments;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.api.event.LifecycleEvent;
import uk.co.enderfall.sdk.api.event.SdkEvents;
import uk.co.enderfall.sdk.api.event.TickEvent;
import uk.co.enderfall.sdk.api.client.ui.ClientScreenRef;
import uk.co.enderfall.sdk.api.client.ui.ClientScreenSpec;
import uk.co.enderfall.sdk.api.client.ui.PortableClientScreen;
import uk.co.enderfall.sdk.api.client.ui.UiFocusManager;
import uk.co.enderfall.sdk.api.client.ui.UiFocusTarget;
import uk.co.enderfall.sdk.api.client.ui.UiRect;
import uk.co.enderfall.sdk.api.client.ui.UiRenderContext;
import uk.co.enderfall.sdk.api.client.ui.UiTextAlign;
import uk.co.enderfall.sdk.api.item.ItemStackRef;
import uk.co.enderfall.sdk.api.entity.EntityTypeRef;
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
        AtomicBoolean reloadRequested = new AtomicBoolean();
        AtomicBoolean reloadCompleted = new AtomicBoolean();
        AtomicBoolean smokeCompleted = new AtomicBoolean();
        AtomicInteger smokeTicks = new AtomicInteger();
        AtomicInteger resourceReloads = new AtomicInteger();
        ClientScreenRef smokeScreen = context.capabilities().supports(Capability.GENERAL_CLIENT_SCREENS)
                ? context.screens().register(context.id("client_contract"),
                        ClientScreenSpec.of("EnderFall Client Contract"),
                        () -> new ContractScreen(screenRendered))
                : null;
        context.resources().onReload(context.id("client_resources"), () -> {
            int count = resourceReloads.incrementAndGet();
            context.logger().info("ENDERFALL_CLIENT_RESOURCES_RELOADED {} {}",
                    context.platform().targetId(), count);
        });
        if (context.capabilities().supports(Capability.CLIENT_COMMANDS)) {
            var operation = Arguments.optional(Arguments.word("operation"));
            context.clientCommands().register(CommandSpec.builder("enderfall_client")
                    .description("Exercises the portable client-command bridge")
                    .argument(operation)
                    .suggests((command, remaining) -> java.util.List.of("reload"))
                    .executes(command -> {
                        String requested = (String) command.arguments().getOrDefault("operation", "reload");
                        if (!"reload".equals(requested)) {
                            command.reply("Usage: /enderfall_client [reload]");
                            return 0;
                        }
                        context.resources().reload();
                        command.reply("EnderFall client resource reload requested");
                        return 1;
                    })
                    .build());
        }
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
            if (resourceReloads.get() >= 1 && reloadRequested.compareAndSet(false, true)) {
                context.resources().reload().whenComplete((unused, failure) -> {
                    if (failure != null) {
                        context.logger().error("ENDERFALL_CLIENT_RESOURCES_RELOAD_FAILED", failure);
                    } else {
                        reloadCompleted.set(true);
                    }
                });
            }
            if (smokeScreen != null && !screenRendered.get() && elapsedTicks >= 20 && elapsedTicks % 20 == 0) {
                // Quick-play test worlds can replace a screen opened during CLIENT_STARTED before its first frame.
                context.screens().open(smokeScreen);
            }
            if (elapsedTicks >= 20 && resourceReloads.get() >= 2 && reloadCompleted.get()
                    && (smokeScreen == null || screenRendered.get())
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
        private final UiFocusManager focus = new UiFocusManager();

        private ContractScreen(AtomicBoolean rendered) {
            this.rendered = rendered;
        }

        @Override public void render(UiRenderContext graphics) {
            rendered.set(true);
            int panelWidth = 220;
            int panelHeight = 100;
            int left = (graphics.frame().width() - panelWidth) / 2;
            int top = (graphics.frame().height() - panelHeight) / 2;
            UiRect itemTarget = new UiRect(left + 10, top + 48, 20, 20);
            UiRect entityTarget = new UiRect(left + 152, top + 28, 58, 66);
            focus.updateTargets(java.util.List.of(
                    UiFocusTarget.enabled("item", itemTarget),
                    UiFocusTarget.enabled("entity", entityTarget)));
            graphics.fill(new UiRect(left, top, panelWidth, panelHeight), 0xE815111D);
            focus.focusedId().ifPresent(id -> graphics.fill(
                    "item".equals(id) ? itemTarget : entityTarget, 0xFFB998FF));
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
            graphics.livingEntity(new EntityTypeRef(ResourceId.of("minecraft", "pig")),
                    new UiRect(left + 154, top + 30, 54, 62),
                    (float) graphics.frame().mouseX(), (float) graphics.frame().mouseY());
        }

        @Override public boolean mouseClicked(double x, double y, int button) {
            return focus.mouseClicked(x, y, button);
        }

        @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
            return focus.keyPressed(key, modifiers);
        }
    }
}
