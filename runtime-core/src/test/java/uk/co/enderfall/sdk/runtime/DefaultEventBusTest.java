package uk.co.enderfall.sdk.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.event.EventKey;
import uk.co.enderfall.sdk.api.event.EventPriority;
import uk.co.enderfall.sdk.api.event.Subscription;

class DefaultEventBusTest {
    private static final EventKey<String> EVENT = new EventKey<>(ResourceId.of("test_mod", "event"), String.class);

    @Test
    void ordersListenersAndIsolatesFailures() {
        RecordingLogger logger = new RecordingLogger();
        DefaultEventBus bus = new DefaultEventBus(logger);
        List<String> calls = new ArrayList<>();
        bus.subscribe(EVENT, EventPriority.LAST, calls::add);
        bus.subscribe(EVENT, EventPriority.FIRST, ignored -> {
            throw new IllegalStateException("expected");
        });
        bus.subscribe(EVENT, EventPriority.NORMAL, value -> calls.add(value + "-normal"));

        bus.publish(EVENT, "payload");

        assertEquals(List.of("payload-normal", "payload"), calls);
        assertEquals(1, logger.failures().size());
    }

    @Test
    void closingSubscriptionStopsCallbacks() {
        RecordingLogger logger = new RecordingLogger();
        DefaultEventBus bus = new DefaultEventBus(logger);
        List<String> calls = new ArrayList<>();
        Subscription subscription = bus.subscribe(EVENT, calls::add);
        subscription.close();
        bus.publish(EVENT, "ignored");
        assertFalse(subscription.active());
        assertEquals(List.of(), calls);
    }

    @Test
    void closingSubscriptionRemovesItRatherThanOnlyMutingIt() {
        RecordingLogger logger = new RecordingLogger();
        DefaultEventBus bus = new DefaultEventBus(logger);
        List<String> calls = new ArrayList<>();

        Subscription first = bus.subscribe(EVENT, value -> calls.add(value + "-first"));
        bus.subscribe(EVENT, value -> calls.add(value + "-second"));
        first.close();

        bus.publish(EVENT, "payload");
        assertEquals(List.of("payload-second"), calls);
    }

    @Test
    void repeatedSubscribeAndCloseDoesNotRetainRegistrations() {
        RecordingLogger logger = new RecordingLogger();
        DefaultEventBus bus = new DefaultEventBus(logger);
        List<String> calls = new ArrayList<>();

        // A long-running server subscribes and unsubscribes per session, menu or block entity.
        // Closed registrations must not accumulate on the hot tick event.
        for (int index = 0; index < 10_000; index++) {
            bus.subscribe(EVENT, calls::add).close();
        }
        assertEquals(0, bus.retainedListenerCount(EVENT));

        Subscription live = bus.subscribe(EVENT, calls::add);
        assertEquals(1, bus.retainedListenerCount(EVENT));

        bus.publish(EVENT, "payload");
        live.close();

        assertEquals(List.of("payload"), calls);
        assertEquals(0, bus.retainedListenerCount(EVENT));
    }

    @Test
    void closingTwiceIsHarmlessAndKeepsOtherListenersRegistered() {
        RecordingLogger logger = new RecordingLogger();
        DefaultEventBus bus = new DefaultEventBus(logger);
        List<String> calls = new ArrayList<>();
        Subscription first = bus.subscribe(EVENT, value -> calls.add(value + "-first"));
        Subscription second = bus.subscribe(EVENT, value -> calls.add(value + "-second"));

        first.close();
        first.close();

        bus.publish(EVENT, "payload");
        assertEquals(List.of("payload-second"), calls);
        assertTrue(second.active());
    }
}
