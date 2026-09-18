package uk.co.enderfall.sdk.api.registry;

import java.util.Objects;

/** Portable properties for a basic block. */
public final class BlockSpec {
    private final uk.co.enderfall.sdk.api.block.PortableBlock behavior;
    public java.util.Optional<uk.co.enderfall.sdk.api.block.PortableBlock> behavior() {
        return java.util.Optional.ofNullable(behavior);
    }
    private final uk.co.enderfall.sdk.api.block.BlockStateDefinition states;
    public uk.co.enderfall.sdk.api.block.BlockStateDefinition states() { return states; }
    private final uk.co.enderfall.sdk.api.block.BlockStateShapes stateShapes;
    public java.util.Optional<uk.co.enderfall.sdk.api.block.BlockStateShapes> stateShapes() { return java.util.Optional.ofNullable(stateShapes); }
    private final boolean horizontalFacing;
    private final boolean sixWayFacing;
    private final uk.co.enderfall.sdk.api.block.SixWayPlacement sixWayPlacement;
    private final boolean waterlogged;
    private final boolean scheduledTicks;
    public boolean sixWayFacing() { return sixWayFacing; }
    public uk.co.enderfall.sdk.api.block.SixWayPlacement sixWayPlacement() { return sixWayPlacement; }
    public boolean horizontalFacing() { return horizontalFacing; }
    public boolean waterlogged() { return waterlogged; }
    public boolean scheduledTicks() { return scheduledTicks; }
    private final uk.co.enderfall.sdk.api.block.BlockShape outlineShape;
    private final uk.co.enderfall.sdk.api.block.BlockShape collisionShape;
    public java.util.Optional<uk.co.enderfall.sdk.api.block.BlockShape> outlineShape() { return java.util.Optional.ofNullable(outlineShape); }
    public java.util.Optional<uk.co.enderfall.sdk.api.block.BlockShape> collisionShape() { return java.util.Optional.ofNullable(collisionShape); }
    /** Properties explicitly overriding a native copy source. */
    public enum Property { STRENGTH, FRICTION, JUMP_FACTOR, LUMINANCE, TOOL, SOUND }
    private final uk.co.enderfall.sdk.api.ResourceId copySource;
    private final java.util.Set<Property> overrides;
    private final float hardness;
    private final float resistance;
    private final float friction;
    private final float jumpFactor;
    private final int luminance;
    private final boolean requiresTool;
    private final SoundPreset sound;

    private BlockSpec(Builder builder) {
        behavior = builder.behavior;
        states = builder.states;
        stateShapes = builder.stateShapes;
        if (stateShapes != null && stateShapes.definition() != states)
            throw new IllegalArgumentException("State shapes must use the block's state definition");
        int facingStates = builder.sixWayFacing ? 6 : builder.horizontalFacing ? 4 : 1;
        int nativeStates = facingStates * (builder.waterlogged ? 2 : 1);
        if (states.stateCount() * nativeStates > uk.co.enderfall.sdk.api.block.BlockStateDefinition.MAX_STATES)
            throw new IllegalArgumentException("Combined native and custom state count exceeds limit");
        if (facingStates > 1 && states.properties().stream().anyMatch(property -> property.name().equals("facing")))
            throw new IllegalArgumentException("Custom facing property conflicts with built-in facing");
        if (builder.waterlogged && states.properties().stream().anyMatch(property -> property.name().equals("waterlogged")))
            throw new IllegalArgumentException("Custom waterlogged property conflicts with built-in waterlogging");
        horizontalFacing = builder.horizontalFacing;
        sixWayFacing = builder.sixWayFacing;
        sixWayPlacement = builder.sixWayPlacement;
        waterlogged = builder.waterlogged;
        scheduledTicks = builder.scheduledTicks;
        outlineShape = builder.outlineShape;
        collisionShape = builder.collisionShape;
        copySource = builder.copySource;
        overrides = java.util.Set.copyOf(builder.overrides);
        hardness = builder.hardness;
        resistance = builder.resistance;
        friction = builder.friction;
        jumpFactor = builder.jumpFactor;
        luminance = builder.luminance;
        requiresTool = builder.requiresTool;
        sound = builder.sound;
    }

    public static Builder builder() {
        return new Builder();
    }

    public java.util.Optional<uk.co.enderfall.sdk.api.ResourceId> copySource() { return java.util.Optional.ofNullable(copySource); }
    public boolean overrides(Property property) { return copySource == null || overrides.contains(property); }

    public float hardness() {
        return hardness;
    }

    public float resistance() {
        return resistance;
    }

    public float friction() {
        return friction;
    }

    public float jumpFactor() {
        return jumpFactor;
    }

    public int luminance() {
        return luminance;
    }

    public boolean requiresTool() {
        return requiresTool;
    }

    public SoundPreset sound() {
        return sound;
    }

    public static final class Builder {
        private uk.co.enderfall.sdk.api.block.PortableBlock behavior;
        Builder behavior(uk.co.enderfall.sdk.api.block.PortableBlock value) {
            behavior = Objects.requireNonNull(value, "behavior"); return this;
        }
        private uk.co.enderfall.sdk.api.block.BlockStateShapes stateShapes;
        /** Sets state-dependent outline and collision; overrides the static shapes. */
        public Builder stateShapes(uk.co.enderfall.sdk.api.block.BlockStateShapes value) {
            stateShapes = Objects.requireNonNull(value, "stateShapes"); return this;
        }
        private uk.co.enderfall.sdk.api.block.BlockStateDefinition states = uk.co.enderfall.sdk.api.block.BlockStateDefinition.builder().build();
        public Builder states(uk.co.enderfall.sdk.api.block.BlockStateDefinition value) {
            states = Objects.requireNonNull(value, "states"); return this;
        }
        private boolean horizontalFacing;
        private boolean sixWayFacing;
        private uk.co.enderfall.sdk.api.block.SixWayPlacement sixWayPlacement =
                uk.co.enderfall.sdk.api.block.SixWayPlacement.VIEW_DIRECTION;
        private boolean waterlogged;
        private boolean scheduledTicks;
        /** Adds north/east/south/west facing, player-facing placement and rotated custom shapes. */
        public Builder horizontalFacing() { horizontalFacing = true; sixWayFacing = false; return this; }
        /** Six-direction facing with placement opposite the player's nearest look direction. */
        public Builder sixWayFacing() {
            return sixWayFacing(uk.co.enderfall.sdk.api.block.SixWayPlacement.VIEW_DIRECTION);
        }
        /** Six-direction facing with an explicit, portable placement rule. */
        public Builder sixWayFacing(uk.co.enderfall.sdk.api.block.SixWayPlacement placement) {
            sixWayFacing = true;
            horizontalFacing = false;
            sixWayPlacement = Objects.requireNonNull(placement, "placement");
            return this;
        }
        /** Enables vanilla water placement, bucket interaction, fluid state and fluid ticking. */
        public Builder waterlogged() { waterlogged = true; return this; }
        /** Enables portable placement, neighbor and scheduled-tick transitions. */
        public Builder scheduledTicks() { scheduledTicks = true; return this; }
        private uk.co.enderfall.sdk.api.block.BlockShape outlineShape;
        private uk.co.enderfall.sdk.api.block.BlockShape collisionShape;
        /** Sets both outline and collision; later individual setters can override either. */
        public Builder shape(uk.co.enderfall.sdk.api.block.BlockShape value) {
            outlineShape = Objects.requireNonNull(value, "value"); collisionShape = value; return this;
        }
        public Builder outlineShape(uk.co.enderfall.sdk.api.block.BlockShape value) {
            outlineShape = Objects.requireNonNull(value, "value"); return this;
        }
        public Builder collisionShape(uk.co.enderfall.sdk.api.block.BlockShape value) {
            collisionShape = Objects.requireNonNull(value, "value"); return this;
        }
        private uk.co.enderfall.sdk.api.ResourceId copySource;
        private final java.util.Set<Property> overrides = java.util.EnumSet.noneOf(Property.class);

        /** Copies target-native properties, not behavior, assets, or state definitions. */
        public Builder copyFrom(uk.co.enderfall.sdk.api.ResourceId source) {
            copySource = Objects.requireNonNull(source, "source");
            overrides.clear();
            return this;
        }

        /** Copies a portable specification, including its native base and overrides. */
        public Builder copyFrom(BlockSpec source) {
            Objects.requireNonNull(source, "source");
            copySource = source.copySource;
            overrides.clear();
            overrides.addAll(source.overrides);
            hardness = source.hardness; resistance = source.resistance;
            friction = source.friction; jumpFactor = source.jumpFactor;
            luminance = source.luminance; requiresTool = source.requiresTool; sound = source.sound;
            return this;
        }
        private float hardness = 1.0F;
        private float resistance = 1.0F;
        private float friction = 0.6F;
        private float jumpFactor = 1.0F;
        private int luminance;
        private boolean requiresTool;
        private SoundPreset sound = SoundPreset.STONE;

        public Builder strength(float hardnessValue, float resistanceValue) {
            if (!Float.isFinite(hardnessValue) || !Float.isFinite(resistanceValue) || hardnessValue < 0 || resistanceValue < 0) {
                throw new IllegalArgumentException("Block strength cannot be negative");
            }
            hardness = hardnessValue;
            overrides.add(Property.STRENGTH);
            resistance = resistanceValue;
            return this;
        }

        public Builder friction(float value) {
            if (!Float.isFinite(value) || value < 0) {
                throw new IllegalArgumentException("friction cannot be negative");
            }
            friction = value;
            overrides.add(Property.FRICTION);
            return this;
        }

        public Builder jumpFactor(float value) {
            if (!Float.isFinite(value) || value < 0) {
                throw new IllegalArgumentException("jumpFactor cannot be negative");
            }
            jumpFactor = value;
            overrides.add(Property.JUMP_FACTOR);
            return this;
        }

        public Builder luminance(int value) {
            if (value < 0 || value > 15) {
                throw new IllegalArgumentException("luminance must be between 0 and 15");
            }
            luminance = value;
            overrides.add(Property.LUMINANCE);
            return this;
        }

        public Builder requiresTool() {
            requiresTool = true;
            overrides.add(Property.TOOL);
            return this;
        }

        public Builder sound(SoundPreset value) {
            sound = Objects.requireNonNull(value, "value");
            overrides.add(Property.SOUND);
            return this;
        }

        public BlockSpec build() {
            return new BlockSpec(this);
        }
    }
}
