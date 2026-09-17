package uk.co.enderfall.sdk.bridge;

/** 26.2 standalone models, resolved while extracting block-entity render state. */
final class ExtractedModelRendererSources {
    private ExtractedModelRendererSources() { }
    static String emit(boolean fabric) {
        return """
                package uk.co.enderfall.sdk.runtime.blockentity.nativebridge;
                ${IMPORT}
                import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
                import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
                import net.minecraft.resources.Identifier;
                import uk.co.enderfall.sdk.api.render.BlockEntityRenderSpec;
                import uk.co.enderfall.sdk.api.render.RenderTransform;
                import com.mojang.blaze3d.vertex.PoseStack;
                import com.mojang.math.Axis;

                public final class PortableModelRenderer {
                    private static final java.util.Map<Identifier, ${KEY}<BlockStateModel>> KEYS = new java.util.HashMap<>();
                    private PortableModelRenderer() { }
                    public static void register(${BUS}BlockEntityRenderSpec spec) {
                        for (var display : spec.models()) {
                            var id = Identifier.parse(display.model().id().toString());
                            if (KEYS.containsKey(id)) continue;
                            ${KEY}<BlockStateModel> key = ${CREATE};
                            KEYS.put(id, key);
                            ${REGISTER}
                        }
                    }
                    record Snapshot(java.util.List<BlockStateModelPart> parts, RenderTransform transform, int light) { }
                        static Snapshot extract(StoredBlockEntity entity, BlockEntityRenderSpec.ModelDisplay display, int fallbackLight, float partialTick) {
                        if (!(entity.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel)) return null;
                        var key = KEYS.get(Identifier.parse(display.model().id().toString()));
                        var model = ${LOOKUP};
                        if (model == null) return null;
                        var parts = new java.util.ArrayList<BlockStateModelPart>();
                        ${COLLECT}
                        var transform = display.sample(entity.renderAnimation(), clientLevel.getGameTime(), partialTick);
                        var level = entity.getLevel();
                        var pos = entity.getBlockPos().offset((int) Math.floor(transform.x()),
                                (int) Math.floor(transform.y()), (int) Math.floor(transform.z()));
                        int light = level != null && level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                                ? net.minecraft.util.LightCoordsUtil.getLightCoords(level, pos) : fallbackLight;
                        return new Snapshot(java.util.List.copyOf(parts), transform, light);
                    }
                    static void submit(Snapshot snapshot, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector) {
                        var transform = snapshot.transform();
                        pose.pushPose();
                        try {
                            pose.translate(transform.x(), transform.y(), transform.z());
                            pose.translate(transform.pivotX(), transform.pivotY(), transform.pivotZ());
                            pose.mulPose(Axis.XP.rotationDegrees(transform.rotationX()));
                            pose.mulPose(Axis.YP.rotationDegrees(transform.rotationY()));
                            pose.mulPose(Axis.ZP.rotationDegrees(transform.rotationZ()));
                            pose.scale(transform.scale(), transform.scale(), transform.scale());
                            pose.translate(-transform.pivotX(), -transform.pivotY(), -transform.pivotZ());
                            collector.submitBlockModel(pose, net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(
                                    Identifier.parse("minecraft:textures/atlas/blocks.png")), snapshot.parts(), new int[0],
                                    snapshot.light(), net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
                        } finally { pose.popPose(); }
                    }
                }
                """.replace("${IMPORT}", fabric ? "import net.fabricmc.fabric.api.client.model.loading.v1.*;"
                        : "import net.neoforged.neoforge.client.model.standalone.*;")
                .replace("${KEY}", fabric ? "ExtraModelKey" : "StandaloneModelKey")
                .replace("${COLLECT}", fabric
                        ? "model.collectParts(net.minecraft.util.RandomSource.create(entity.getBlockPos().hashCode()), parts);"
                        : "model.collectParts(clientLevel, entity.getBlockPos(), entity.getBlockState(), net.minecraft.util.RandomSource.create(entity.getBlockPos().hashCode()), parts);")
                .replace("${BUS}", fabric ? "" : "net.neoforged.bus.api.IEventBus bus, ")
                .replace("${CREATE}", fabric ? "ExtraModelKey.create(id::toString)" : "new StandaloneModelKey<>(id::toString)")
                .replace("${REGISTER}", fabric
                        ? "ModelLoadingPlugin.register(context -> context.addModel(key, SimpleUnbakedExtraModel.blockStateModel(id)));"
                        : "bus.addListener((net.neoforged.neoforge.client.event.ModelEvent.RegisterStandalone event) -> event.register(key, SimpleUnbakedStandaloneModel.blockStateModel(id)));")
                .replace("${LOOKUP}", fabric
                        ? "((FabricModelManager) net.minecraft.client.Minecraft.getInstance().getModelManager()).getModel(key)"
                        : "net.minecraft.client.Minecraft.getInstance().getModelManager().getStandaloneModel(key)");
    }
}
