package uk.co.enderfall.sdk.api.event;

import uk.co.enderfall.sdk.api.ResourceId;

/** Stable built-in events. */
public final class SdkEvents {
    public static final EventKey<LifecycleEvent> LIFECYCLE = key("lifecycle", LifecycleEvent.class);
    public static final EventKey<TickEvent> TICK = key("tick", TickEvent.class);
    public static final EventKey<PlayerEvent> PLAYER = key("player", PlayerEvent.class);
    public static final EventKey<InteractionEvent> INTERACTION = key("interaction", InteractionEvent.class);

    private SdkEvents() {
    }

    private static <E> EventKey<E> key(String path, Class<E> eventType) {
        return new EventKey<>(ResourceId.of("enderfall_sdk", path), eventType);
    }
}
