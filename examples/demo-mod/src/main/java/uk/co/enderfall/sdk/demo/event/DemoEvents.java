package uk.co.enderfall.sdk.demo.event;

import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.event.SdkEvents;
import uk.co.enderfall.sdk.demo.config.DemoConfig;

/** Lifecycle, tick, and interaction callbacks remain independent from content registration. */
public final class DemoEvents {
    private DemoEvents() {
    }

    public static void register(ModContext context, DemoConfig config) {
        context.events().subscribe(SdkEvents.LIFECYCLE,
                event -> context.logger().info("Lifecycle {} on {}", event.stage(), context.platform().targetId()));
        context.events().subscribe(SdkEvents.TICK, event -> {
            if (event.side() == uk.co.enderfall.sdk.api.event.TickEvent.Side.SERVER
                    && event.phase() == uk.co.enderfall.sdk.api.event.TickEvent.Phase.END
                    && event.tick() > 0 && event.tick() % 1_200 == 0 && config.enabled()) {
                context.logger().debug("Demo heartbeat at server tick {}", event.tick());
            }
        });
    }
}
