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
    private final ResourceId heldItem;
    private final Hand hand;
    private final boolean sneaking;
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
        this(kind, side, playerId, target, blockLocation,
                kind == Kind.USE_ITEM ? target : null, null, false);
    }

    /** Complete native interaction context. Older event producers may omit item/hand details. */
    public InteractionEvent(Kind kind, Side side, UUID playerId, ResourceId target,
            uk.co.enderfall.sdk.api.blockentity.BlockLocation blockLocation,
            ResourceId heldItem, Hand hand, boolean sneaking) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.side = Objects.requireNonNull(side, "side");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.target = Objects.requireNonNull(target, "target");
        if (blockLocation != null && kind != Kind.USE_BLOCK) {
            throw new IllegalArgumentException("Only block interactions can include a block location");
        }
        if (kind == Kind.USE_ITEM && heldItem != null && !target.equals(heldItem)) {
            throw new IllegalArgumentException("A direct item interaction must target the held item");
        }
        this.blockLocation = blockLocation;
        this.heldItem = heldItem;
        this.hand = hand;
        this.sneaking = sneaking;
    }

    /** Exact dimension and hit-block coordinates when supplied by the native hook. Never inferred. */
    public java.util.Optional<uk.co.enderfall.sdk.api.blockentity.BlockLocation> blockLocation() {
        return java.util.Optional.ofNullable(blockLocation);
    }

    /** Held item for direct item use or use-on-block when supplied by the native hook. */
    public java.util.Optional<ResourceId> heldItem() { return java.util.Optional.ofNullable(heldItem); }

    /** Hand used for this interaction when supplied by the native hook. */
    public java.util.Optional<Hand> hand() { return java.util.Optional.ofNullable(hand); }

    public boolean sneaking() { return sneaking; }

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

    public enum Hand {
        MAIN_HAND,
        OFF_HAND
    }
}
