package uk.co.enderfall.sdk.api.event;

import java.util.Objects;
import java.util.UUID;
import uk.co.enderfall.sdk.api.ResourceId;

/** Portable item/block interaction event with loader-neutral cancellation. */
public final class InteractionEvent {
    private final Kind kind;
    private final UUID playerId;
    private final ResourceId target;
    private boolean cancelled;

    public InteractionEvent(Kind kind, UUID playerId, ResourceId target) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.target = Objects.requireNonNull(target, "target");
    }

    public Kind kind() {
        return kind;
    }

    public UUID playerId() {
        return playerId;
    }

    public ResourceId target() {
        return target;
    }

    public boolean cancelled() {
        return cancelled;
    }

    public void cancel() {
        cancelled = true;
    }

    public enum Kind {
        USE_ITEM,
        USE_BLOCK
    }
}
