package uk.co.enderfall.sdk.bridge;

/** Shared pre-26 standalone-model drawing with explicit registration ABI choices. */
final class StandaloneModelRendererSources {
    private StandaloneModelRendererSources() { }
    static boolean supports(BlockEntityNativePolicy policy) {
        return true;
    }
    static String emit(BlockEntityNativePolicy policy) {
        if (policy.unobfuscated()) return ExtractedModelRendererSources.emit(policy.fabric());
        if (!supports(policy)) throw new IllegalArgumentException("Unsupported standalone model target: " + policy);
        return """
                package uk.co.enderfall.sdk.runtime.blockentity.nativebridge;

                import com.mojang.blaze3d.vertex.PoseStack;
                import com.mojang.math.Axis;
                import net.minecraft.client.Minecraft;
                import net.minecraft.client.renderer.MultiBufferSource;
                import net.minecraft.client.renderer.RenderType;
                import net.minecraft.resources.ResourceLocation;
                import uk.co.enderfall.sdk.api.render.BlockEntityRenderSpec;

                public final class PortableModelRenderer {
                    private PortableModelRenderer() { }
                    private static ResourceLocation parse(String value) {
                        return java.util.Objects.requireNonNull(ResourceLocation.tryParse(value));
                    }
                    public static void register(${BUS_PARAMETER}BlockEntityRenderSpec spec) {
                        if (spec.models().isEmpty()) return;
                        var ids = spec.models().stream().map(display -> ResourceLocation.parse(display.model().id().toString()))
                                .distinct().toList();
                        ${REGISTER_MODELS}
                    }
                    static void render(StoredBlockEntity entity, BlockEntityRenderSpec.ModelDisplay display,
                        PoseStack pose, MultiBufferSource buffers, int fallbackLight, int overlay, float partialTick) {
                        var level = entity.getLevel();
                        if (level == null || !level.isClientSide || entity.isRemoved()) return;
                        var id = ResourceLocation.parse(display.model().id().toString());
                        // Resolve from the current manager on every frame, never retain stale baked models after reload.
                        var manager = Minecraft.getInstance().getModelManager();
                        var model = ${LOOKUP};
                        if (model == null) model = manager.getMissingModel();
                        var transform = display.sample(entity.renderAnimation(), level.getGameTime(), partialTick);
                        var sample = entity.getBlockPos().offset((int) Math.floor(transform.x()),
                                (int) Math.floor(transform.y()), (int) Math.floor(transform.z()));
                        int light = level.getChunkSource().hasChunk(sample.getX() >> 4, sample.getZ() >> 4)
                                ? net.minecraft.client.renderer.LevelRenderer.getLightColor(level, sample) : fallbackLight;
                        pose.pushPose();
                        try {
                            pose.translate(transform.x(), transform.y(), transform.z());
                            pose.translate(transform.pivotX(), transform.pivotY(), transform.pivotZ());
                            pose.mulPose(Axis.XP.rotationDegrees(transform.rotationX()));
                            pose.mulPose(Axis.YP.rotationDegrees(transform.rotationY()));
                            pose.mulPose(Axis.ZP.rotationDegrees(transform.rotationZ()));
                            pose.scale(transform.scale(), transform.scale(), transform.scale());
                            pose.translate(-transform.pivotX(), -transform.pivotY(), -transform.pivotZ());
                            var layer = RenderType.entityCutoutNoCull(ResourceLocation.parse("minecraft:textures/atlas/blocks.png"));
                            Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                                    buffers.getBuffer(layer), null, model, 1, 1, 1, light, overlay${MODEL_DATA});
                        } finally { pose.popPose(); }
                    }
                }
                """.replace("${BUS_PARAMETER}", policy.fabric() ? "" : (policy.legacy() ? "net.minecraftforge.eventbus.api.IEventBus" : "net.neoforged.bus.api.IEventBus") + " bus, ")
                .replace("${REGISTER_MODELS}", policy.fabric()
                        ? "net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(context -> context.addModels(ids));"
                        : "bus.addListener((net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) -> { for (var id : ids) event.register(${MODEL_KEY}); });")
                .replace("${MODEL_DATA}", policy.fabric() ? "" : ", net.neoforged.neoforge.client.model.data.ModelData.EMPTY, layer")
                .replace("${MODEL_KEY}", policy.legacy() || policy.modernRecipes() ? "id"
                        : "net.minecraft.client.resources.model.ModelResourceLocation.standalone(id)")
                .replace("${LOOKUP}", policy.fabric()
                        ? "((net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager) manager).getModel(id)"
                        : policy.legacy() ? "manager.getModel(id)" : policy.modernRecipes() ? "manager.getStandaloneModel(id)"
                        : "manager.getModel(net.minecraft.client.resources.model.ModelResourceLocation.standalone(id))")
                .replace("ResourceLocation.parse(", "parse(")
                .replace("net.neoforged.neoforge", policy.legacy() ? "net.minecraftforge" : "net.neoforged.neoforge");
    }
}
