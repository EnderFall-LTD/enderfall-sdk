package uk.co.enderfall.sdk.api.event;

public interface EventBus {
    default <E> Subscription subscribe(EventKey<E> event, EventListener<? super E> listener) {
        return subscribe(event, EventPriority.NORMAL, listener);
    }

    <E> Subscription subscribe(EventKey<E> event, EventPriority priority, EventListener<? super E> listener);
}
