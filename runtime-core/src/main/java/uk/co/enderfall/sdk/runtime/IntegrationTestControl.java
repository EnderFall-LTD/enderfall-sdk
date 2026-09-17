package uk.co.enderfall.sdk.runtime;

import java.util.concurrent.atomic.AtomicInteger;

/** Internal opt-in coordination used only by real-game integration processes. */
public final class IntegrationTestControl {
    private static final String CLIENT_SMOKE_ENVIRONMENT = "ENDERFALL_CLIENT_SMOKE";
    private static final String CLIENT_COMPLETE_PROPERTY =
            "uk.co.enderfall.sdk.test.clientSmokeComplete";
    private static final int POST_COMPLETION_TICKS = 100;
    private static final AtomicInteger completedClientTicks = new AtomicInteger();

    private IntegrationTestControl() {
    }

    /**
     * Returns true after the portable contract mod has completed its client assertions and the
     * initial resource reload has had time to settle.
     *
     * @return whether the native client adapter should request a graceful stop
     */
    public static boolean shouldStopSmokeClient() {
        if (!"true".equalsIgnoreCase(System.getenv(CLIENT_SMOKE_ENVIRONMENT))
                || !Boolean.getBoolean(CLIENT_COMPLETE_PROPERTY)) {
            return false;
        }
        int tick = completedClientTicks.incrementAndGet();
        if (tick == 1) {
            System.out.println("ENDERFALL_CLIENT_SMOKE_STOP_ARMED");
        }
        if (tick == POST_COMPLETION_TICKS) {
            System.out.println("ENDERFALL_CLIENT_SMOKE_STOP_REQUESTED");
        }
        return tick >= POST_COMPLETION_TICKS;
    }
}
