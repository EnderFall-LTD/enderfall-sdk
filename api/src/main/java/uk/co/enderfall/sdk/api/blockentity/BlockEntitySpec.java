package uk.co.enderfall.sdk.api.blockentity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import uk.co.enderfall.sdk.api.annotation.Experimental;
import uk.co.enderfall.sdk.api.registry.BlockRef;

/** Shared storage definition. Building a spec does not register a native block entity. */
@Experimental("Block-entity persistence foundation; native registration is not available yet")
public final class BlockEntitySpec {
    public static final int MAXIMUM_SLOTS = 256;
    public static final int MAXIMUM_FIELDS = 32;
    private final BlockRef block;
    private final java.util.Set<String> animations;
    private final String menuOpenAnimation;
    private final String menuCloseAnimation;
    private final int inventorySlots;
    private final java.util.Set<Integer> renderSlots;
    private final java.util.Set<String> renderTanks;
    private final Map<String, BlockEntityInt> fields;
    private final BlockEntityTicker serverTicker;
    private final Map<String, uk.co.enderfall.sdk.api.fluid.FluidTankSpec> tanks;

    private BlockEntitySpec(Builder builder) {
        block = builder.block;
        animations = java.util.Set.copyOf(builder.animations);
        menuOpenAnimation = builder.menuOpenAnimation;
        menuCloseAnimation = builder.menuCloseAnimation;
        if (menuOpenAnimation != null && (!animations.contains(menuOpenAnimation) || !animations.contains(menuCloseAnimation))) {
            throw new IllegalArgumentException("Menu animations must be declared");
        }
        inventorySlots = builder.inventorySlots;
        for (int slot : builder.renderSlots) {
            if (slot >= inventorySlots) throw new IllegalArgumentException("Render slot is outside the inventory: " + slot);
        }
        renderSlots = java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(builder.renderSlots));
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(builder.fields));
        serverTicker = builder.serverTicker;
        tanks = Collections.unmodifiableMap(new LinkedHashMap<>(builder.tanks));
        if (!tanks.keySet().containsAll(builder.renderTanks)) throw new IllegalArgumentException("Render tank must be declared in storage");
        renderTanks = Collections.unmodifiableSet(new java.util.TreeSet<>(builder.renderTanks));
    }

    public static Builder builder(BlockRef block) { return new Builder(block); }
    public BlockRef block() { return block; }
    /** Public visual animation names; one active animation per block instance. */
    public java.util.Set<String> animations() { return animations; }
    public java.util.Optional<String> menuOpenAnimation() { return java.util.Optional.ofNullable(menuOpenAnimation); }
    public java.util.Optional<String> menuCloseAnimation() { return java.util.Optional.ofNullable(menuCloseAnimation); }
    public Map<String, uk.co.enderfall.sdk.api.fluid.FluidTankSpec> tanks() { return tanks; }
    public int inventorySlots() { return inventorySlots; }
    /** Slots explicitly permitted in nearby clients' visual snapshots. Empty by default.
     * Item data in these slots is public to tracking clients; never expose private inventory here.
     */
    public java.util.Set<Integer> renderSlots() { return renderSlots; }
    /** Tank identities and amounts explicitly public to tracking clients. Empty by default. */
    public java.util.Set<String> renderTanks() { return renderTanks; }
    public Map<String, BlockEntityInt> fields() { return fields; }
    public java.util.Optional<BlockEntityTicker> serverTicker() { return java.util.Optional.ofNullable(serverTicker); }

    public static final class Builder {
        private final BlockRef block;
        private final java.util.Set<String> animations = new java.util.TreeSet<>();
        private String menuOpenAnimation;
        private String menuCloseAnimation;
        public Builder animation(String name) {
            uk.co.enderfall.sdk.api.render.AnimationPlaybackState.playing(name, 0);
            if (animations.contains(name) || animations.size() >= 32) throw new IllegalArgumentException("Duplicate or excessive animations");
            animations.add(name);
            return this;
        }
        /** Timed workbench: first viewer opens, last viewer closes. Names must be declared. */
        public Builder menuAnimations(String open, String close) {
            menuOpenAnimation = Objects.requireNonNull(open, "open");
            menuCloseAnimation = Objects.requireNonNull(close, "close");
            return this;
        }
        private int inventorySlots;
        private final java.util.Set<Integer> renderSlots = new java.util.TreeSet<>();
        private final java.util.Set<String> renderTanks = new java.util.TreeSet<>();

        /** Exposes a declared tank for visual synchronization; does not grant mutation access. */
        public Builder renderTank(String name) {
            Objects.requireNonNull(name, "name");
            if (!name.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("Invalid render tank name");
            if (renderTanks.contains(name) || renderTanks.size() >= 16) throw new IllegalArgumentException("Duplicate or excessive render tanks");
            renderTanks.add(name);
            return this;
        }

        /** Opts one slot into visual synchronization. Native snapshot transport is experimental. */
        public Builder renderSlot(int slot) {
            if (slot < 0 || slot >= MAXIMUM_SLOTS) throw new IllegalArgumentException("Invalid render slot: " + slot);
            if (renderSlots.contains(slot)) throw new IllegalArgumentException("Duplicate render slot: " + slot);
            if (renderSlots.size() >= 64) throw new IllegalArgumentException("At most 64 render slots are supported");
            renderSlots.add(slot);
            return this;
        }
        private final Map<String, BlockEntityInt> fields = new LinkedHashMap<>();
        private BlockEntityTicker serverTicker;
        private final Map<String, uk.co.enderfall.sdk.api.fluid.FluidTankSpec> tanks = new LinkedHashMap<>();

        public Builder tank(uk.co.enderfall.sdk.api.fluid.FluidTankSpec tank) {
            Objects.requireNonNull(tank, "tank");
            if (tanks.containsKey(tank.name()) || tanks.size() >= 16) {
                throw new IllegalArgumentException("Duplicate tank or more than 16 tanks");
            }
            tanks.put(tank.name(), tank);
            return this;
        }

        private Builder(BlockRef block) {
            this.block = Objects.requireNonNull(block, "block");
            // ResourceId uses an ASCII alphabet, so this is also its UTF-8 byte length.
            if (block.id().toString().length() > 1024) {
                throw new IllegalArgumentException("Block-entity block ID exceeds 1024 bytes");
            }
        }

        public Builder inventorySlots(int count) {
            if (count < 0 || count > MAXIMUM_SLOTS) {
                throw new IllegalArgumentException("Inventory slots must be between 0 and " + MAXIMUM_SLOTS);
            }
            inventorySlots = count;
            return this;
        }

        public Builder field(BlockEntityInt field) {
            Objects.requireNonNull(field, "field");
            if (fields.containsKey(field.name())) {
                throw new IllegalArgumentException("Duplicate block-entity field: " + field.name());
            }
            if (fields.size() >= MAXIMUM_FIELDS) {
                throw new IllegalArgumentException("Too many block-entity fields");
            }
            fields.put(field.name(), field);
            return this;
        }

        /** Optional loaded-block server behaviour; no ticker is installed by default. */
        public Builder serverTicker(BlockEntityTicker ticker) {
            serverTicker = Objects.requireNonNull(ticker, "ticker");
            return this;
        }

        public BlockEntitySpec build() { return new BlockEntitySpec(this); }
    }
}
