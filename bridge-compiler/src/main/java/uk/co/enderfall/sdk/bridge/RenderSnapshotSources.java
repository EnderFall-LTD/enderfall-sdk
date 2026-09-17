package uk.co.enderfall.sdk.bridge;

/** Visual-only vanilla block updates, separate from world-save data. */
final class RenderSnapshotSources {
    private RenderSnapshotSources() { }
    static String emit(BlockEntityNativePolicy policy) {
        return """
                    private static final String RENDER_KEY = "enderfall_render_items";
                    private static final String RENDER_TANK_KEY = "enderfall_render_tanks";
                    private static final String RENDER_ANIMATION_KEY = "enderfall_render_animation";
                    private uk.co.enderfall.sdk.api.render.AnimationPlaybackState renderAnimation;
                    public uk.co.enderfall.sdk.api.render.AnimationPlaybackState renderAnimation() {
                        return level != null && level.isClientSide ? renderAnimation : null;
                    }
                    private uk.co.enderfall.sdk.runtime.blockentity.RenderInventorySnapshot<ItemStack> renderSnapshot;
                    private uk.co.enderfall.sdk.runtime.blockentity.RenderTankSnapshot renderTankSnapshot;
                    public java.util.Optional<uk.co.enderfall.sdk.api.fluid.FluidVolume> renderTank(String name) {
                        if (level == null || !level.isClientSide || !definition.renderTanks().contains(name)) return java.util.Optional.empty();
                        return renderTankSnapshot == null ? java.util.Optional.empty() : renderTankSnapshot.contents(name);
                    }
                    @Override public void setChanged() {
                        super.setChanged();
                        if (!definition.renderTanks().isEmpty()) notifyRenderSnapshot();
                    }
                    private void applyVisualSnapshot(byte[] items, byte[] tanks, byte[] animation) {
                        if ((long) items.length + tanks.length + animation.length > 32000) throw new IllegalArgumentException("Visual snapshot exceeds combined byte limit");
                        var itemReplacement = uk.co.enderfall.sdk.runtime.blockentity.RenderInventorySnapshot
                                .decode(items, definition, new NativeStacks());
                        var tankReplacement = uk.co.enderfall.sdk.runtime.blockentity.RenderTankSnapshot.decode(tanks, definition);
                        var animationReplacement = uk.co.enderfall.sdk.runtime.blockentity.RenderAnimationSnapshot.decode(animation, definition.animations());
                        renderSnapshot = itemReplacement;
                        renderTankSnapshot = tankReplacement;
                        renderAnimation = animationReplacement;
                    }
                    private boolean renderSnapshotWarning;
                    public ItemStack renderStack(int slot) {
                        if (level == null || !level.isClientSide || !definition.renderSlots().contains(slot)) return ItemStack.EMPTY;
                        return renderSnapshot == null ? ItemStack.EMPTY : renderSnapshot.stack(slot);
                    }
                    private void notifyRenderSnapshot() {
                        if ((definition.renderSlots().isEmpty() && definition.renderTanks().isEmpty() && definition.animations().isEmpty()) || level == null || level.isClientSide || isRemoved()) return;
                        try { level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2); }
                        catch (RuntimeException failure) {
                            System.getLogger("enderfall_sdk").log(System.Logger.Level.ERROR,
                                    "Could not notify visual inventory for " + definition.block().id());
                        }
                    }
                    @Override
                    public CompoundTag getUpdateTag(${ARGS}) {
                        CompoundTag tag = new CompoundTag();
                        if (definition.renderSlots().isEmpty() && definition.renderTanks().isEmpty() && definition.animations().isEmpty()) return tag;
                        serializationRegistries = ${REGISTRIES};
                        try {
                            byte[] payload;
                            byte[] tankPayload;
                            byte[] animationPayload = uk.co.enderfall.sdk.runtime.blockentity.RenderAnimationSnapshot.encode(animations.snapshot().orElse(null));
                            try {
                                payload = uk.co.enderfall.sdk.runtime.blockentity.RenderInventorySnapshot
                                        .capture(definition, inventory::getItem, new NativeStacks()).encode();
                                tankPayload = uk.co.enderfall.sdk.runtime.blockentity.RenderTankSnapshot.capture(definition,
                                        name -> storage.tank(definition.tanks().get(name)).contents()).encode();
                                if ((long) payload.length + tankPayload.length + animationPayload.length > 32000) throw new IllegalArgumentException("Combined visual data exceeds limit");
                                renderSnapshotWarning = false;
                            } catch (RuntimeException invalid) {
                                // Hide invalid visuals without modifying inventory or failing a committed transfer.
                                payload = uk.co.enderfall.sdk.runtime.blockentity.RenderInventorySnapshot
                                        .capture(definition, slot -> ItemStack.EMPTY, new NativeStacks()).encode();
                                tankPayload = uk.co.enderfall.sdk.runtime.blockentity.RenderTankSnapshot.capture(definition,
                                        name -> java.util.Optional.empty()).encode();
                                if (!renderSnapshotWarning) {
                                    renderSnapshotWarning = true;
                                    System.getLogger("enderfall_sdk").log(System.Logger.Level.WARNING,
                                            "Visual inventory hidden: cannot encode snapshot for " + definition.block().id());
                                }
                            }
                            tag.putByteArray(RENDER_KEY, payload);
                            tag.putByteArray(RENDER_TANK_KEY, tankPayload);
                            tag.putByteArray(RENDER_ANIMATION_KEY, animationPayload);
                            return tag;
                        } finally { serializationRegistries = null; }
                    }
                    @Override
                    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
                        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
                    }
                """.replace("${ARGS}", policy.legacy() ? "" : "HolderLookup.Provider registries")
                .replace("${REGISTRIES}", policy.legacy() ? "null" : "registries");
    }
    static String load(BlockEntityNativePolicy policy) {
        if (policy.unobfuscated()) return """
                        if (tag.read(RENDER_KEY, com.mojang.serialization.Codec.PASSTHROUGH).isPresent()) {
                            if (!hasByteArray(tag, RENDER_KEY) || !hasByteArray(tag, RENDER_TANK_KEY)) throw new IllegalArgumentException("Invalid visual inventory tag");
                            serializationRegistries = snapshotLookup(tag);
                            try {
                                if (!hasByteArray(tag, RENDER_ANIMATION_KEY)) throw new IllegalArgumentException("Missing animation snapshot");
                                applyVisualSnapshot(readBytes(tag, RENDER_KEY), readBytes(tag, RENDER_TANK_KEY), readBytes(tag, RENDER_ANIMATION_KEY));
                            } finally { serializationRegistries = null; }
                            return;
                        }
                """;
        return """
                        if (tag.contains(RENDER_KEY)) {
                            if (!tag.contains(RENDER_KEY, Tag.TAG_BYTE_ARRAY) || !tag.contains(RENDER_TANK_KEY, Tag.TAG_BYTE_ARRAY)) throw new IllegalArgumentException("Invalid visual inventory tag");
                            serializationRegistries = ${REGISTRIES};
                            try {
                                if (!tag.contains(RENDER_ANIMATION_KEY, Tag.TAG_BYTE_ARRAY)) throw new IllegalArgumentException("Missing animation snapshot");
                                applyVisualSnapshot(tag.getByteArray(RENDER_KEY), tag.getByteArray(RENDER_TANK_KEY), tag.getByteArray(RENDER_ANIMATION_KEY));
                            } finally { serializationRegistries = null; }
                            return;
                        }
                """.replace("${REGISTRIES}", policy.legacy() ? "null" : "registries");
    }
}
