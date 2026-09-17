package uk.co.enderfall.sdk.api.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import uk.co.enderfall.sdk.api.annotation.Experimental;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;
import uk.co.enderfall.sdk.api.registry.ItemRef;

/** Immutable item and fluid display plan. Uses native world lighting.
 * Inventory displays must use a synchronized client snapshot, never server-owned storage.
 * Empty slots draw nothing. Native bridges must reject slots outside the registered storage.
 */
@Experimental("Portable block-entity rendering; visual compatibility validation is ongoing")
public final class BlockEntityRenderSpec {
    public sealed interface ItemSource permits FixedItem, InventorySlot { }
    public record FixedItem(ItemRef item) implements ItemSource {
        public FixedItem { Objects.requireNonNull(item, "item"); }
    }
    public record InventorySlot(int slot) implements ItemSource {
        public InventorySlot {
            if (slot < 0 || slot >= BlockEntitySpec.MAXIMUM_SLOTS) {
                throw new IllegalArgumentException("Render slot must be between 0 and 255");
            }
        }
    }
    public record ItemDisplay(ItemSource source, RenderTransform transform, ItemRenderPose pose, RenderAnimation animation) {
        public ItemDisplay(ItemSource source, RenderTransform transform, ItemRenderPose pose) {
            this(source, transform, pose, null);
        }
        public RenderTransform sample(double gameTicks) {
            return animation == null ? transform : animation.sample(gameTicks);
        }
        public ItemDisplay(ItemSource source, RenderTransform transform) {
            this(source, transform, ItemRenderPose.FIXED);
        }
        public ItemDisplay {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(transform, "transform");
            Objects.requireNonNull(pose, "pose");
        }
    }

    private final List<ItemDisplay> items;
    public record FluidDisplay(uk.co.enderfall.sdk.api.fluid.FluidTankSpec tank, FluidRenderBounds bounds) {
        public FluidDisplay { Objects.requireNonNull(tank, "tank"); Objects.requireNonNull(bounds, "bounds"); }
    }
    private final List<FluidDisplay> fluids;
    public record ModelDisplay(ModelRef model, RenderTransform transform, RenderAnimation animation,
                               java.util.Map<String, RenderAnimation> namedAnimations) {
        public ModelDisplay(ModelRef model, RenderTransform transform, RenderAnimation animation) {
            this(model, transform, animation, java.util.Map.of());
        }
        public RenderTransform sample(AnimationPlaybackState state, long tick, float partialTick) {
            if (namedAnimations.isEmpty()) return sample(tick + (double) partialTick);
            if (state == null) return transform;
            var definition = namedAnimations.get(state.animation());
            return definition == null ? transform : state.sample(definition, tick, partialTick);
        }
        public ModelDisplay(ModelRef model, RenderTransform transform) { this(model, transform, null); }
        public RenderTransform sample(double gameTicks) {
            return animation == null ? transform : animation.sample(gameTicks);
        }
        public ModelDisplay {
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(transform, "transform");
            namedAnimations = java.util.Map.copyOf(namedAnimations);
            if (namedAnimations.size() > 32 || (animation != null && !namedAnimations.isEmpty())) {
                throw new IllegalArgumentException("Invalid named animation plan");
            }
            namedAnimations.forEach((name, value) -> {
                AnimationPlaybackState.playing(name, 0);
                if (value.startTick() != 0) throw new IllegalArgumentException("Named animations must start at relative tick zero");
            });
        }
    }
    private final List<ModelDisplay> models;
    private BlockEntityRenderSpec(List<ItemDisplay> items, List<FluidDisplay> fluids, List<ModelDisplay> models) {
        this.items = List.copyOf(items); this.fluids = List.copyOf(fluids);
        this.models = List.copyOf(models);
    }
    public List<ItemDisplay> items() { return items; }
    public List<FluidDisplay> fluids() { return fluids; }
    public List<ModelDisplay> models() { return models; }
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final List<ItemDisplay> items = new ArrayList<>();
        private final List<FluidDisplay> fluids = new ArrayList<>();
        private final List<ModelDisplay> models = new ArrayList<>();
        private Builder() { }
        public Builder namedModel(ModelRef model, RenderTransform idle, java.util.Map<String, RenderAnimation> animations) {
            if (animations.isEmpty()) throw new IllegalArgumentException("Named model needs animations");
            var display = new ModelDisplay(model, idle, null, animations);
            if (models.size() >= 64) throw new IllegalArgumentException("At most 64 model displays are supported");
            models.add(display);
            return this;
        }
        public Builder animatedItem(ItemRef item, RenderAnimation animation) {
            return animatedItem(item, animation, ItemRenderPose.FIXED);
        }
        public Builder animatedItem(ItemRef item, RenderAnimation animation, ItemRenderPose pose) {
            Objects.requireNonNull(animation, "animation");
            return add(new ItemDisplay(new FixedItem(item), animation.transition().from(), pose, animation));
        }
        public Builder animatedInventorySlot(int slot, RenderAnimation animation) {
            return animatedInventorySlot(slot, animation, ItemRenderPose.FIXED);
        }
        public Builder animatedInventorySlot(int slot, RenderAnimation animation, ItemRenderPose pose) {
            Objects.requireNonNull(animation, "animation");
            return add(new ItemDisplay(new InventorySlot(slot), animation.transition().from(), pose, animation));
        }
        /** Declares a world-time-driven ping-pong model animation. */
        public Builder animatedModel(ModelRef model, RenderAnimation animation) {
            Objects.requireNonNull(animation, "animation");
            if (models.size() >= 64) throw new IllegalArgumentException("At most 64 model displays are supported");
            models.add(new ModelDisplay(model, animation.transition().from(), animation));
            return this;
        }
        /** Declares a raw model with a block-corner origin; unsupported targets reject registration. */
        public Builder model(ModelRef model, RenderTransform transform) {
            var display = new ModelDisplay(model, transform);
            if (models.size() >= 64) throw new IllegalArgumentException("At most 64 model displays are supported");
            models.add(display);
            return this;
        }
        /** Declares a fluid cuboid using an explicitly exposed common tank. */
        public Builder fluid(uk.co.enderfall.sdk.api.fluid.FluidTankSpec tank, FluidRenderBounds bounds) {
            var display = new FluidDisplay(tank, bounds);
            if (fluids.size() >= 16) throw new IllegalArgumentException("At most 16 fluid displays are supported");
            fluids.add(display);
            return this;
        }
        public Builder item(ItemRef item, RenderTransform transform) {
            return add(new ItemDisplay(new FixedItem(item), transform));
        }
        public Builder item(ItemRef item, RenderTransform transform, ItemRenderPose pose) {
            return add(new ItemDisplay(new FixedItem(item), transform, pose));
        }
        public Builder inventorySlot(int slot, RenderTransform transform) {
            return add(new ItemDisplay(new InventorySlot(slot), transform));
        }
        public Builder inventorySlot(int slot, RenderTransform transform, ItemRenderPose pose) {
            return add(new ItemDisplay(new InventorySlot(slot), transform, pose));
        }
        private Builder add(ItemDisplay display) {
            if (items.size() >= 64) throw new IllegalArgumentException("A renderer supports at most 64 item displays");
            items.add(display);
            return this;
        }
        public BlockEntityRenderSpec build() {
            if (items.isEmpty() && fluids.isEmpty() && models.isEmpty()) throw new IllegalArgumentException("A renderer must contain at least one display");
            return new BlockEntityRenderSpec(items, fluids, models);
        }
    }
}
