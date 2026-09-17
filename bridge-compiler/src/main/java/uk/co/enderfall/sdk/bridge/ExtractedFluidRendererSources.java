package uk.co.enderfall.sdk.bridge;

/** 26.2 fluid model resolution happens during extraction, never in a draw callback. */
final class ExtractedFluidRendererSources {
    private ExtractedFluidRendererSources() { }
    static String emit() {
        return """
                package uk.co.enderfall.sdk.runtime.blockentity.nativebridge;

                import uk.co.enderfall.sdk.api.render.BlockEntityRenderSpec;
                import uk.co.enderfall.sdk.runtime.blockentity.FluidCuboidMesh;

                final class PortableFluidRenderer {
                    private PortableFluidRenderer() { }
                    record Snapshot(java.util.List<FluidCuboidMesh.Face> faces, float u0, float u1,
                            float v0, float v1, int color, int light) { }
                    @SuppressWarnings("deprecation") // Use the shared baked-model tint and emission contract on both loaders.
                    static Snapshot extract(StoredBlockEntity entity, BlockEntityRenderSpec.FluidDisplay display, int fallbackLight) {
                        var contents = entity.renderTank(display.tank().name());
                        var level = entity.getLevel();
                        if (contents.isEmpty() || !(level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel)) return null;
                        var volume = contents.get();
                        var id = net.minecraft.resources.Identifier.parse(volume.fluid().toString());
                        var registry = net.minecraft.core.registries.BuiltInRegistries.FLUID;
                        if (!registry.containsKey(id)) return null;
                        var fluid = registry.getValue(id);
                        if (fluid == net.minecraft.world.level.material.Fluids.EMPTY) return null;
                        var fluidState = fluid.defaultFluidState();
                        var model = net.minecraft.client.Minecraft.getInstance().getModelManager()
                                .getFluidStateModelSet().get(fluidState);
                        var sprite = model.stillMaterial().sprite();
                        var blockState = fluidState.createLegacyBlock();
                        int color = model.tintSource() == null ? -1
                                : model.tintSource().colorInWorld(blockState, clientLevel, entity.getBlockPos());
                        var lightPos = entity.getBlockPos().above();
                        int light = level.getChunkSource().hasChunk(lightPos.getX() >> 4, lightPos.getZ() >> 4)
                                ? net.minecraft.util.LightCoordsUtil.getLightCoords(level, lightPos) : fallbackLight;
                        light = (light & ~0xffff) | Math.max(light & 0xffff, blockState.getLightEmission() << 4);
                        return new Snapshot(FluidCuboidMesh.create(display.bounds(), volume.amount(), display.tank().capacity()),
                                sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV1(), color, light);
                    }
                    static void submit(Snapshot snapshot, com.mojang.blaze3d.vertex.PoseStack pose,
                            net.minecraft.client.renderer.SubmitNodeCollector collector) {
                        collector.submitCustomGeometry(pose, net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(
                                net.minecraft.resources.Identifier.parse("minecraft:textures/atlas/blocks.png")), (transform, consumer) -> {
                            int color = snapshot.color();
                            for (var face : snapshot.faces()) {
                                for (var point : java.util.List.of(face.a(), face.b(), face.c(), face.d())) {
                                    double localU = face.u(point);
                                    double localV = face.v(point);
                                    float u = snapshot.u0() + (snapshot.u1() - snapshot.u0()) * (float) localU;
                                    float v = snapshot.v0() + (snapshot.v1() - snapshot.v0()) * (float) localV;
                                    consumer.addVertex(transform.pose(), (float) point.x(), (float) point.y(), (float) point.z())
                                            .setColor((color >>> 16) & 255, (color >>> 8) & 255, color & 255, color >>> 24)
                                            .setUv(u, v).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                                            .setLight(snapshot.light()).setNormal(transform, face.normalX(), face.normalY(), face.normalZ());
                                }
                            }
                        });
                    }
                }
                """;
    }
}
