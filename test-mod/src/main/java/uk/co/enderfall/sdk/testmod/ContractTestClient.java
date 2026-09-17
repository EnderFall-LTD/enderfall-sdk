package uk.co.enderfall.sdk.testmod;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import uk.co.enderfall.sdk.api.ClientModContext;
import uk.co.enderfall.sdk.api.EnderfallClientMod;
import uk.co.enderfall.sdk.api.config.ConfigScope;
import uk.co.enderfall.sdk.api.config.ConfigSpec;
import uk.co.enderfall.sdk.api.event.LifecycleEvent;
import uk.co.enderfall.sdk.api.event.SdkEvents;
import uk.co.enderfall.sdk.api.event.TickEvent;

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
        AtomicInteger smokeTicks = new AtomicInteger();
        ConfigSpec.Builder clientConfig = ConfigSpec.builder();
        clientConfig.booleanValue("show_diagnostics", true, "Show client contract diagnostics.");
        context.configs().register("client", ConfigScope.CLIENT, clientConfig.build());
        context.events().subscribe(SdkEvents.LIFECYCLE, event -> {
            if (event.stage() == LifecycleEvent.Stage.CLIENT_STARTED) {
                context.logger().info("ENDERFALL_CLIENT_SMOKE_READY {}", context.platform().targetId());
                clientStarted.set(true);
            }
        });
        context.events().subscribe(SdkEvents.TICK, event -> {
            if (event.side() == TickEvent.Side.CLIENT && event.phase() == TickEvent.Phase.END
                    && event.tick() == 1) {
                context.logger().debug("Client tick {}", event.tick());
            }
            if (smokeMode && !connectionMode && clientStarted.get() && event.side() == TickEvent.Side.CLIENT
                    && event.phase() == TickEvent.Phase.END && smokeTicks.incrementAndGet() == 20) {
                context.logger().info("ENDERFALL_CLIENT_SMOKE_COMPLETE {}", context.platform().targetId());
                System.out.flush();
                System.err.flush();
                System.setProperty(CLIENT_COMPLETE_PROPERTY, "true");
            }
        });
    }
}
