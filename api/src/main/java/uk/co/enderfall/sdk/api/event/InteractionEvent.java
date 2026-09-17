package uk.co.enderfall.sdk.api.event;

import java.util.Objects;
import java.util.UUID;
import uk.co.enderfall.sdk.api.ResourceId;

/** Portable item/block interaction event with loader-neutral cancellation. */
public final class InteractionEvent {
    private final Kind kind;
    private final Side side;
    private final UUID playerId;
    private final ResourceId target;
    private final uk.co.enderfall.sdk.api.blockentity.BlockLocation blockLocation;
    private boolean handled;
    private boolean cancelled;

    public InteractionEvent(Kind kind, UUID playerId, ResourceId target) {
        this(kind, Side.SERVER, playerId, target);
    }

    public InteractionEvent(Kind kind, Side side, UUID playerId, ResourceId target) {
        this(kind, side, playerId, target, null);
    }

    /** Native block-use hooks supply the hit position; legacy callers may omit it. */
    public InteractionEvent(Kind kind, Side side, UUID playerId, ResourceId target,
            uk.co.enderfall.sdk.api.blockentity.BlockLocation blockLocation) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.side = Objects.requireNonNull(side, "side");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.target = Objects.requireNonNull(target, "target");
        if (blockLocation != null && kind != Kind.USE_BLOCK) {
            throw new IllegalArgumentException("Only block interactions can include a block location");
        }
        this.blockLocation = blockLocation;
    }

    /** Exact dimension and hit-block coordinates when supplied by the native hook. Never inferred. */
    public java.util.Optional<uk.co.enderfall.sdk.api.blockentity.BlockLocation> blockLocation() {
        return java.util.Optional.ofNullable(blockLocation);
    }

    public Kind kind() {
        return kind;
    }

    public UUID playerId() {
        return playerId;
    }

    public Side side() {
        return side;
    }

    public ResourceId target() {
        return target;
    }

    public boolean cancelled() {
        return cancelled;
    }

    /** Marks the interaction successful and prevents vanilla fallback handling. */
    public void handle() {
        handled = true;
        cancelled = false;
    }

    public boolean handled() {
        return handled;
    }

    public void cancel() {
        handled = false;
        cancelled = true;
    }

    public enum Side {
        CLIENT,
        SERVER
    }

    public enum Kind {
        USE_ITEM,
        USE_BLOCK
    }
}
