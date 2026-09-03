package uk.co.enderfall.sdk.api.registry;

import java.util.Objects;

/** Portable properties for a basic block. */
public final class BlockSpec {
    private final float hardness;
    private final float resistance;
    private final float friction;
    private final float jumpFactor;
    private final int luminance;
    private final boolean requiresTool;
    private final SoundPreset sound;

    private BlockSpec(Builder builder) {
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
        private float hardness = 1.0F;
        private float resistance = 1.0F;
        private float friction = 0.6F;
        private float jumpFactor = 1.0F;
        private int luminance;
        private boolean requiresTool;
        private SoundPreset sound = SoundPreset.STONE;

        public Builder strength(float hardnessValue, float resistanceValue) {
            if (hardnessValue < 0 || resistanceValue < 0) {
                throw new IllegalArgumentException("Block strength cannot be negative");
            }
            hardness = hardnessValue;
            resistance = resistanceValue;
            return this;
        }

        public Builder friction(float value) {
            if (value < 0) {
                throw new IllegalArgumentException("friction cannot be negative");
            }
            friction = value;
            return this;
        }

        public Builder jumpFactor(float value) {
            if (value < 0) {
                throw new IllegalArgumentException("jumpFactor cannot be negative");
            }
            jumpFactor = value;
            return this;
        }

        public Builder luminance(int value) {
            if (value < 0 || value > 15) {
                throw new IllegalArgumentException("luminance must be between 0 and 15");
            }
            luminance = value;
            return this;
        }

        public Builder requiresTool() {
            requiresTool = true;
            return this;
        }

        public Builder sound(SoundPreset value) {
            sound = Objects.requireNonNull(value, "value");
            return this;
        }

        public BlockSpec build() {
            return new BlockSpec(this);
        }
    }
}
