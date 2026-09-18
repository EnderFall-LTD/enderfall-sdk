package uk.co.enderfall.sdk.api.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.item.ItemDataKey;
import uk.co.enderfall.sdk.api.item.RepairMaterial;
import uk.co.enderfall.sdk.api.item.TooltipColor;
import uk.co.enderfall.sdk.api.item.TooltipLine;
import uk.co.enderfall.sdk.api.item.TooltipVisibility;

/** Portable properties shared by basic items and class-based {@code PortableItem} definitions. */
public final class ItemSpec {
    private final int maxStackSize;
    private final int durability;
    private final boolean fireResistant;
    private final Rarity rarity;
    private final List<TooltipLine> tooltipLines;
    private final List<ItemDataKey<?>> dataKeys;
    private final RepairMaterial repairMaterial;

    private ItemSpec(Builder builder) {
        maxStackSize = builder.maxStackSize;
        durability = builder.durability;
        fireResistant = builder.fireResistant;
        rarity = builder.rarity;
        tooltipLines = List.copyOf(builder.tooltipLines);
        dataKeys = List.copyOf(builder.dataKeys);
        repairMaterial = builder.repairMaterial;
        if (durability > 0 && maxStackSize != 1) {
            throw new IllegalArgumentException("Durable items must have maxStackSize 1");
        }
        if (repairMaterial != null && durability == 0) {
            throw new IllegalArgumentException("Repair materials require a durable item");
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

    public List<TooltipLine> tooltipLines() {
        return tooltipLines;
    }

    public List<ItemDataKey<?>> dataKeys() { return dataKeys; }

    public Optional<RepairMaterial> repairMaterial() { return Optional.ofNullable(repairMaterial); }

    public Optional<ItemDataKey<?>> dataKey(ResourceId id) {
        Objects.requireNonNull(id, "id");
        return dataKeys.stream().filter(key -> key.id().equals(id)).findFirst();
    }

    public static final class Builder {
        public Builder copyFrom(ItemSpec source) {
            Objects.requireNonNull(source, "source");
            maxStackSize = source.maxStackSize; durability = source.durability;
            fireResistant = source.fireResistant; rarity = source.rarity;
            tooltipLines.clear(); tooltipLines.addAll(source.tooltipLines);
            dataKeys.clear(); dataKeys.addAll(source.dataKeys);
            repairMaterial = source.repairMaterial;
            return this;
        }
        private int maxStackSize = 64;
        private int durability;
        private boolean fireResistant;
        private Rarity rarity = Rarity.COMMON;
        private final List<TooltipLine> tooltipLines = new ArrayList<>();
        private final List<ItemDataKey<?>> dataKeys = new ArrayList<>();
        private RepairMaterial repairMaterial;

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

        /** Adds one fully configured portable tooltip line. */
        public Builder tooltip(TooltipLine line) {
            if (tooltipLines.size() >= 64) {
                throw new IllegalStateException("Items cannot declare more than 64 tooltip lines");
            }
            tooltipLines.add(Objects.requireNonNull(line, "line"));
            return this;
        }

        /** Adds an always-visible translated gray line. */
        public Builder tooltip(String translationKey) {
            return tooltip(TooltipLine.translated(translationKey));
        }

        /** Adds a translated line shown while Shift is held. */
        public Builder shiftTooltip(String translationKey) {
            return tooltip(TooltipLine.translated(translationKey, TooltipColor.GRAY,
                    TooltipVisibility.SHIFT_DOWN));
        }

        /** Adds a translated hint shown while Shift is not held. */
        public Builder shiftHint(String translationKey) {
            return tooltip(TooltipLine.translated(translationKey, TooltipColor.DARK_GRAY,
                    TooltipVisibility.SHIFT_UP));
        }

        /** Declares one typed field that may be persisted and synchronized on this item stack. */
        public Builder data(ItemDataKey<?> key) {
            Objects.requireNonNull(key, "key");
            if (dataKeys.size() >= 32) {
                throw new IllegalStateException("Items cannot declare more than 32 portable data keys");
            }
            if (dataKeys.stream().anyMatch(existing -> existing.id().equals(key.id()))) {
                throw new IllegalArgumentException("Duplicate item data key " + key.id());
            }
            dataKeys.add(key);
            return this;
        }

        /** Accepts one exact registered item ID as an anvil repair material. */
        public Builder repairItem(ResourceId itemId) {
            return repairWith(RepairMaterial.item(itemId));
        }

        /** Accepts every item in a vanilla or modded item tag as an anvil repair material. */
        public Builder repairTag(ResourceId tagId) {
            return repairWith(RepairMaterial.tag(tagId));
        }

        private Builder repairWith(RepairMaterial material) {
            Objects.requireNonNull(material, "material");
            if (repairMaterial != null) {
                throw new IllegalStateException("An item can declare one repair item or one repair tag");
            }
            repairMaterial = material;
            return this;
        }

        public ItemSpec build() {
            return new ItemSpec(this);
        }
    }
}
