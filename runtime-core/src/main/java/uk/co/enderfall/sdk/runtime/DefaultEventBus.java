package uk.co.enderfall.sdk.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import uk.co.enderfall.sdk.api.event.EventBus;
import uk.co.enderfall.sdk.api.event.EventKey;
import uk.co.enderfall.sdk.api.event.EventListener;
import uk.co.enderfall.sdk.api.event.EventPriority;
import uk.co.enderfall.sdk.api.event.Subscription;
import uk.co.enderfall.sdk.api.logging.ModLogger;

public final class DefaultEventBus implements EventBus {
    private final Map<EventKey<?>, CopyOnWriteArrayList<RegisteredListener<?>>> listeners = new ConcurrentHashMap<>();
    private final ModLogger logger;

    public DefaultEventBus(ModLogger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public <E> Subscription subscribe(EventKey<E> event, EventPriority priority,
                                      EventListener<? super E> listener) {
        Objects.requireNonNull(event, "event");
        RegisteredListener<E> registration = new RegisteredListener<>(priority, listener);
        listeners.computeIfAbsent(event, ignored -> new CopyOnWriteArrayList<>()).add(registration);
        return registration;
    }

    public <E> void publish(EventKey<E> key, E event) {
        Objects.requireNonNull(event, "event");
        if (!key.eventType().isInstance(event)) {
            throw new IllegalArgumentException("Event " + key.id() + " expected " + key.eventType().getName());
        }
        CopyOnWriteArrayList<RegisteredListener<?>> registrations = listeners.get(key);
        if (registrations == null) {
            return;
        }
        ArrayList<RegisteredListener<?>> ordered = new ArrayList<>(registrations);
        ordered.sort(Comparator.comparing(RegisteredListener::priority));
        for (RegisteredListener<?> registration : ordered) {
            registration.invoke(event, key, logger);
        }
    }

    private static final class RegisteredListener<E> implements Subscription {
        private final EventPriority priority;
        private final EventListener<? super E> listener;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private RegisteredListener(EventPriority priority, EventListener<? super E> listener) {
            this.priority = Objects.requireNonNull(priority, "priority");
            this.listener = Objects.requireNonNull(listener, "listener");
        }

        private EventPriority priority() {
            return priority;
        }

        @SuppressWarnings("unchecked")
        private void invoke(Object event, EventKey<?> key, ModLogger logger) {
            if (!active.get()) {
                return;
            }
            try {
                listener.handle((E) event);
            } catch (Exception exception) {
                logger.error("Listener for {} failed", exception, key.id());
            }
        }

        @Override
        public boolean active() {
            return active.get();
        }

        @Override
        public void close() {
            active.set(false);
        }
    }
}
