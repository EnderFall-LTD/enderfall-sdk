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
    private final boolean creativeMode;
    private final uk.co.enderfall.sdk.api.item.MutableItemData itemData;
    private final uk.co.enderfall.sdk.api.item.MutableItemStack itemStack;
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
                kind == Kind.USE_ITEM ? target : null, null, false, false, null, null);
    }

    /** Complete native interaction context. Older event producers may omit item/hand details. */
    public InteractionEvent(Kind kind, Side side, UUID playerId, ResourceId target,
            uk.co.enderfall.sdk.api.blockentity.BlockLocation blockLocation,
            ResourceId heldItem, Hand hand, boolean sneaking) {
        this(kind, side, playerId, target, blockLocation, heldItem, hand, sneaking, null);
    }

    /** Complete native interaction context including the real held stack's portable data. */
    public InteractionEvent(Kind kind, Side side, UUID playerId, ResourceId target,
            uk.co.enderfall.sdk.api.blockentity.BlockLocation blockLocation,
            ResourceId heldItem, Hand hand, boolean sneaking,
            uk.co.enderfall.sdk.api.item.MutableItemData itemData) {
        this(kind, side, playerId, target, blockLocation, heldItem, hand, sneaking,
                false, itemData, null);
    }

    /** Complete native interaction context, including game mode and held-stack access. */
    public InteractionEvent(Kind kind, Side side, UUID playerId, ResourceId target,
            uk.co.enderfall.sdk.api.blockentity.BlockLocation blockLocation,
            ResourceId heldItem, Hand hand, boolean sneaking, boolean creativeMode,
            uk.co.enderfall.sdk.api.item.MutableItemData itemData,
            uk.co.enderfall.sdk.api.item.MutableItemStack itemStack) {
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
        this.creativeMode = creativeMode;
        this.itemData = itemData;
        this.itemStack = itemStack;
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

    /** Whether the interacting player currently has creative instant-build abilities. */
    public boolean creativeMode() { return creativeMode; }

    /**
     * The actual held stack when supplied by a native hook. It is readable on either side,
     * but mutation is accepted only by server-side views.
     */
    public java.util.Optional<uk.co.enderfall.sdk.api.item.MutableItemStack> itemStack() {
        return java.util.Optional.ofNullable(itemStack);
    }

    /**
     * Typed data on the actual held stack. Present only for SDK items with declared keys.
     * Native bridges make writes server-only; normal save, copy, drop and inventory sync
     * semantics are provided by Minecraft's stack serialization.
     */
    public java.util.Optional<uk.co.enderfall.sdk.api.item.MutableItemData> itemData() {
        return java.util.Optional.ofNullable(itemData);
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

    public enum Hand {
        MAIN_HAND,
        OFF_HAND
    }
}
