package uk.co.enderfall.sdk.api.registry;

import java.util.Objects;

/** Portable properties for a basic item. Custom subclasses belong in native roots. */
public final class ItemSpec {
    private final int maxStackSize;
    private final int durability;
    private final boolean fireResistant;
    private final Rarity rarity;

    private ItemSpec(Builder builder) {
        maxStackSize = builder.maxStackSize;
        durability = builder.durability;
        fireResistant = builder.fireResistant;
        rarity = builder.rarity;
        if (durability > 0 && maxStackSize != 1) {
            throw new IllegalArgumentException("Durable items must have maxStackSize 1");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public int maxStackSize() {
        return maxStackSize;
    }

    public int durability() {
        return durability;
    }

    public boolean fireResistant() {
        return fireResistant;
    }

    public Rarity rarity() {
        return rarity;
    }

    public static final class Builder {
        private int maxStackSize = 64;
        private int durability;
        private boolean fireResistant;
        private Rarity rarity = Rarity.COMMON;

        public Builder maxStackSize(int value) {
            if (value < 1 || value > 64) {
                throw new IllegalArgumentException("maxStackSize must be between 1 and 64");
            }
            maxStackSize = value;
            return this;
        }

        public Builder durability(int value) {
            if (value < 0) {
                throw new IllegalArgumentException("durability cannot be negative");
            }
            durability = value;
            return this;
        }

        public Builder fireResistant() {
            fireResistant = true;
            return this;
        }

        public Builder rarity(Rarity value) {
            rarity = Objects.requireNonNull(value, "value");
            return this;
        }

        public ItemSpec build() {
            return new ItemSpec(this);
        }
    }
}
