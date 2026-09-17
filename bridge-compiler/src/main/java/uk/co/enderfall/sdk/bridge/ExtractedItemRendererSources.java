package uk.co.enderfall.sdk.bridge;

/** 26.2 copies visuals during extraction; submit never touches world or inventory. */
final class ExtractedItemRendererSources {
    private ExtractedItemRendererSources() { }
    static String emit(String registration, boolean models) {
        return """
                package uk.co.enderfall.sdk.runtime.blockentity.nativebridge;

                import com.mojang.blaze3d.vertex.PoseStack;
                import com.mojang.math.Axis;
                import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
                import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
                import net.minecraft.client.renderer.item.ItemModelResolver;
                import net.minecraft.client.renderer.item.ItemStackRenderState;
                import uk.co.enderfall.sdk.api.render.BlockEntityRenderSpec;
                import uk.co.enderfall.sdk.api.render.RenderTransform;

                public final class PortableItemRenderer implements BlockEntityRenderer<StoredBlockEntity, PortableItemRenderer.State> {
                    private final BlockEntityRenderSpec spec;
                    private final ItemModelResolver resolver;
                    private PortableItemRenderer(BlockEntityRenderSpec spec, ItemModelResolver resolver) {
                        this.spec = spec; this.resolver = resolver;
                    }
                    ${REGISTER}
                    public static final class State extends BlockEntityRenderState {
                        private final java.util.List<Display> displays = new java.util.ArrayList<>();
                        private final java.util.List<PortableFluidRenderer.Snapshot> fluids = new java.util.ArrayList<>();
                        ${MODEL_STATE}
                    }
                    private record Display(ItemStackRenderState item, RenderTransform transform, int light) { }
                    @Override public State createRenderState() { return new State(); }
                    @Override public void extractRenderState(StoredBlockEntity entity, State state, float partialTick,
                            net.minecraft.world.phys.Vec3 camera,
                            net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay overlay) {
                        BlockEntityRenderer.super.extractRenderState(entity, state, partialTick, camera, overlay);
                        state.displays.clear();
                        state.fluids.clear();
                        ${MODEL_CLEAR}
                        var level = entity.getLevel();
                        if (level == null || !level.isClientSide() || entity.isRemoved()) return;
                        ${MODEL_EXTRACT}
                        for (var display : spec.fluids()) {
                            var snapshot = PortableFluidRenderer.extract(entity, display, state.lightCoords);
                            if (snapshot != null) state.fluids.add(snapshot);
                        }
                        for (var display : spec.items()) {
                            net.minecraft.world.item.ItemStack stack;
                            if (display.source() instanceof BlockEntityRenderSpec.InventorySlot slot) {
                                stack = entity.renderStack(slot.slot());
                            } else {
                                var fixed = (BlockEntityRenderSpec.FixedItem) display.source();
                                var id = net.minecraft.resources.Identifier.parse(fixed.item().id().toString());
                                stack = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id));
                            }
                            if (stack.isEmpty()) continue;
                            var itemState = new ItemStackRenderState();
                            resolver.updateForTopItem(itemState, stack, PortableItemPose.resolve(display.pose()),
                                    level, null, entity.getBlockPos().hashCode());
                            var transform = display.sample(level.getGameTime() + (double) partialTick);
                            var lightPos = entity.getBlockPos().offset((int) Math.floor(transform.x()),
                                    (int) Math.floor(transform.y()), (int) Math.floor(transform.z()));
                            int displayLight = level.getChunkSource().hasChunk(lightPos.getX() >> 4, lightPos.getZ() >> 4)
                                    ? net.minecraft.util.LightCoordsUtil.getLightCoords(level, lightPos) : state.lightCoords;
                            state.displays.add(new Display(itemState, transform, displayLight));
                        }
                    }
                    @Override public void submit(State state, PoseStack pose,
                            net.minecraft.client.renderer.SubmitNodeCollector collector,
                            net.minecraft.client.renderer.state.level.CameraRenderState camera) {
                        for (var display : state.displays) {
                            var transform = display.transform();
                            pose.pushPose();
                            try {
                                pose.translate(transform.x(), transform.y(), transform.z());
                                pose.translate(transform.pivotX(), transform.pivotY(), transform.pivotZ());
                                pose.mulPose(Axis.XP.rotationDegrees(transform.rotationX()));
                                pose.mulPose(Axis.YP.rotationDegrees(transform.rotationY()));
                                pose.mulPose(Axis.ZP.rotationDegrees(transform.rotationZ()));
                                pose.scale(transform.scale(), transform.scale(), transform.scale());
                                pose.translate(-transform.pivotX(), -transform.pivotY(), -transform.pivotZ());
                                display.item().submit(pose, collector, display.light(),
                                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
                            } finally { pose.popPose(); }
                        }
                        for (var fluid : state.fluids) PortableFluidRenderer.submit(fluid, pose, collector);
                        ${MODEL_SUBMIT}
                    }
                }
                """.replace("${REGISTER}", registration)
                .replace("${MODEL_STATE}", models ? "private final java.util.List<PortableModelRenderer.Snapshot> models = new java.util.ArrayList<>();" : "")
                .replace("${MODEL_CLEAR}", models ? "state.models.clear();" : "")
                .replace("${MODEL_EXTRACT}", models ? "for (var display : spec.models()) { var snapshot = PortableModelRenderer.extract(entity, display, state.lightCoords, partialTick); if (snapshot != null) state.models.add(snapshot); }" : "")
                .replace("${MODEL_SUBMIT}", models ? "for (var model : state.models) PortableModelRenderer.submit(model, pose, collector);" : "");
    }
}
