package uk.co.enderfall.sdk.bridge;

/** Immediate rendering ABI for pre-extraction targets. */
final class ImmediateFluidRendererSources {
    private ImmediateFluidRendererSources() { }
    static String emit(BlockEntityNativePolicy policy) {
        String appearance = policy.fabric() ? """
                    var handler = net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry.INSTANCE.get(fluid);
                    if (handler == null) return;
                    var sprites = handler.getFluidSprites(level, pos, state);
                    if (sprites == null || sprites.length == 0 || sprites[0] == null) return;
                    var sprite = sprites[0];
                    int color = 0xff000000 | handler.getFluidColor(level, pos, state);
                """ : """
                    var extensions = ${EXTENSIONS}.of(fluid);
                    var texture = extensions.getStillTexture(state, level, pos);
                    if (texture == null) return;
                    var sprite = net.minecraft.client.Minecraft.getInstance().getTextureAtlas(
                            ATLAS).apply(texture);
                    int color = extensions.getTintColor(state, level, pos);
                """.replace("${EXTENSIONS}", policy.legacy()
                        ? "net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions"
                        : "net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions");
        String vertex = policy.legacy() ? """
                    consumer.vertex(pose.last().pose(), (float) point.x(), (float) point.y(), (float) point.z())
                            .color(red, green, blue, alpha).uv(u, v).overlayCoords(overlay).uv2(light)
                            .normal(pose.last().normal(), face.normalX(), face.normalY(), face.normalZ()).endVertex();
                """ : """
                    consumer.addVertex(pose.last().pose(), (float) point.x(), (float) point.y(), (float) point.z())
                            .setColor(red, green, blue, alpha).setUv(u, v).setOverlay(overlay).setLight(light)
                            .setNormal(pose.last(), face.normalX(), face.normalY(), face.normalZ());
                """;
        return """
                package uk.co.enderfall.sdk.runtime.blockentity.nativebridge;

                import com.mojang.blaze3d.vertex.PoseStack;
                import net.minecraft.client.renderer.MultiBufferSource;
                import net.minecraft.core.registries.BuiltInRegistries;
                import net.minecraft.resources.ResourceLocation;
                import uk.co.enderfall.sdk.api.render.BlockEntityRenderSpec;
                import uk.co.enderfall.sdk.runtime.blockentity.FluidCuboidMesh;

                /** Generated client-only fluid drawing. Reads only public tank snapshots. */
                final class PortableFluidRenderer {
                    private static final ResourceLocation ATLAS = java.util.Objects.requireNonNull(ResourceLocation.tryParse("minecraft:textures/atlas/blocks.png"));
                    private PortableFluidRenderer() { }
                    static void render(StoredBlockEntity entity, BlockEntityRenderSpec.FluidDisplay display,
                            PoseStack pose, MultiBufferSource buffers, int fallbackLight, int overlay) {
                        var contents = entity.renderTank(display.tank().name());
                        if (contents.isEmpty()) return;
                        var volume = contents.get();
                        var level = entity.getLevel();
                        if (level == null || !level.isClientSide) return;
                        var pos = entity.getBlockPos();
                        var id = ${ID};
                        if (!${REGISTRY}.containsKey(id)) return;
                        var fluid = ${LOOKUP};
                        if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) return;
                        var state = fluid.defaultFluidState();
                        ${APPEARANCE}
                        var lightPos = pos.above();
                        int light = level.getChunkSource().hasChunk(lightPos.getX() >> 4, lightPos.getZ() >> 4)
                                ? net.minecraft.client.renderer.LevelRenderer.getLightColor(level, lightPos) : fallbackLight;
                        int emission = ${EMISSION};
                        light = (light & ~0xffff) | Math.max(light & 0xffff, emission << 4);
                        int red = (color >>> 16) & 255, green = (color >>> 8) & 255, blue = color & 255, alpha = color >>> 24;
                        var consumer = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(
                                ATLAS));
                        for (var face : FluidCuboidMesh.create(display.bounds(), volume.amount(), display.tank().capacity())) {
                            for (var point : java.util.List.of(face.a(), face.b(), face.c(), face.d())) {
                                // Block-local UVs retain texture scale as the tank drains.
                                double localU = face.u(point);
                                double localV = face.v(point);
                                float u = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * (float) localU;
                                float v = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * (float) localV;
                                ${VERTEX}
                            }
                        }
                    }
                }
                """.replace("${ID}", policy.legacy()
                        ? "java.util.Objects.requireNonNull(ResourceLocation.tryParse(volume.fluid().toString()))"
                        : "ResourceLocation.parse(volume.fluid().toString())")
                .replace("${REGISTRY}", policy.legacy() && !policy.fabric() ? "net.minecraftforge.registries.ForgeRegistries.FLUIDS" : "BuiltInRegistries.FLUID")
                .replace("${LOOKUP}", policy.legacy() && !policy.fabric() ? "net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(id)"
                        : policy.modernRecipes() ? "BuiltInRegistries.FLUID.getValue(id)" : "BuiltInRegistries.FLUID.get(id)")
                .replace("${APPEARANCE}", appearance)
                .replace("${EMISSION}", policy.fabric() ? "state.createLegacyBlock().getLightEmission()" : "fluid.getFluidType().getLightLevel()")
                .replace("${VERTEX}", vertex);
    }
}
