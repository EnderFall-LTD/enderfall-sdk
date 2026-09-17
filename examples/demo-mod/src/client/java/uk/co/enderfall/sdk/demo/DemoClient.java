package uk.co.enderfall.sdk.demo;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import uk.co.enderfall.sdk.api.ClientModContext;
import uk.co.enderfall.sdk.api.EnderfallClientMod;
import uk.co.enderfall.sdk.api.event.LifecycleEvent;
import uk.co.enderfall.sdk.api.event.SdkEvents;
import uk.co.enderfall.sdk.api.event.TickEvent;

/** Client-only entrypoint; no Minecraft or loader imports are required. */
public final class DemoClient implements EnderfallClientMod {
    private static final String CLIENT_COMPLETE_PROPERTY =
            "uk.co.enderfall.sdk.test.clientSmokeComplete";

    @Override
    public void initialize(ClientModContext context) {
        context.logger().info("EnderFall SDK demo client services initialized");
        if (!"true".equalsIgnoreCase(System.getenv("ENDERFALL_CLIENT_SMOKE"))) {
            return;
        }

        System.clearProperty(CLIENT_COMPLETE_PROPERTY);
        AtomicBoolean clientStarted = new AtomicBoolean();
        AtomicInteger readyTicks = new AtomicInteger();
        context.events().subscribe(SdkEvents.LIFECYCLE, event -> {
            if (event.stage() == LifecycleEvent.Stage.CLIENT_STARTED) {
                context.logger().info("ENDERFALL_CLIENT_SMOKE_READY {}", context.platform().targetId());
                clientStarted.set(true);
            }
        });
        context.events().subscribe(SdkEvents.TICK, event -> {
            if (clientStarted.get() && event.side() == TickEvent.Side.CLIENT
                    && event.phase() == TickEvent.Phase.END && readyTicks.incrementAndGet() == 20) {
                context.logger().info("ENDERFALL_CLIENT_SMOKE_COMPLETE {}", context.platform().targetId());
                System.out.flush();
                System.err.flush();
                System.setProperty(CLIENT_COMPLETE_PROPERTY, "true");
            }
        });
    }
}
