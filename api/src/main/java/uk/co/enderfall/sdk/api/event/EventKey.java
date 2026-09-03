package uk.co.enderfall.sdk.api.event;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

/** Strongly typed event identifier. */
public record EventKey<E>(ResourceId id, Class<E> eventType) {
    public EventKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(eventType, "eventType");
    }
}
