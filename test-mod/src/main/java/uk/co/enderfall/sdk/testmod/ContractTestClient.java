package uk.co.enderfall.sdk.testmod;

import uk.co.enderfall.sdk.api.ClientModContext;
import uk.co.enderfall.sdk.api.EnderfallClientMod;
import uk.co.enderfall.sdk.api.event.SdkEvents;

public final class ContractTestClient implements EnderfallClientMod {
    @Override
    public void initialize(ClientModContext context) {
        context.events().subscribe(SdkEvents.TICK, event -> {
            if (event.side() == uk.co.enderfall.sdk.api.event.TickEvent.Side.CLIENT) {
                context.logger().debug("Client tick {}", event.tick());
            }
        });
    }
}
