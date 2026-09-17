package uk.co.enderfall.sdk.bridge;

/** Shared immediate-mode item renderer; client classes never appear in portable API/storage. */
final class BlockEntityItemRendererSources {
    private BlockEntityItemRendererSources() { }

    static String registration(BlockEntityNativePolicy policy) {
        return """
                    @Override
                    public void registerBlockEntityRenderer(uk.co.enderfall.sdk.api.registry.BlockRef block,
                            uk.co.enderfall.sdk.api.render.BlockEntityRenderSpec spec) {
                        if (platformInfo.environment() != Environment.CLIENT) {
                            throw new IllegalStateException("Block-entity renderers are client-only");
                        }
                        var binding = persistentBlocks.get(block.id());
                        if (binding == null) throw new IllegalArgumentException("Unknown persistent renderer block: " + block.id());
                        ${MODEL_GATE}
                        for (var display : spec.models()) {
                            if (!binding.definition().animations().containsAll(display.namedAnimations().keySet())) {
                                throw new IllegalArgumentException("Model animation names must be declared in common storage");
                            }
                        }
                        for (var display : spec.fluids()) {
                            if (!binding.definition().renderTanks().contains(display.tank().name())
                                    || !display.tank().equals(binding.definition().tanks().get(display.tank().name()))) {
                                throw new IllegalArgumentException("Renderer tank must match an exposed common tank definition");
                            }
                        }
                        for (var display : spec.items()) {
                            if (display.source() instanceof uk.co.enderfall.sdk.api.render.BlockEntityRenderSpec.InventorySlot slot) {
                                if (!binding.definition().renderSlots().contains(slot.slot())) {
                                    throw new IllegalArgumentException("Renderer slot is not exposed by the common block definition");
                                }
                                continue;
                            }
                            var item = (uk.co.enderfall.sdk.api.render.BlockEntityRenderSpec.FixedItem) display.source();
                            if (!items.containsKey(item.item().id()) && !persistentBlocks.containsKey(item.item().id())
                                    && !${ITEM_REGISTRY}.containsKey(${LOOKUP_ID}(item.item().id()))) {
                                throw new IllegalArgumentException("Unknown renderer item: " + item.item().id());
                            }
                        }
                        ${REGISTER}
                    }
                """.replace("${LOOKUP_ID}", policy.unobfuscated() ? "identifier" : "location")
                .replace("${MODEL_GATE}", StandaloneModelRendererSources.supports(policy)
                        ? "" : "if (!spec.models().isEmpty()) throw new UnsupportedOperationException(\"Custom model drawing is not implemented on this target yet\");")
                .replace("${REGISTER}", policy.fabric()
                ? (StandaloneModelRendererSources.supports(policy)
                        ? "uk.co.enderfall.sdk.runtime.blockentity.nativebridge.PortableModelRenderer.register(spec);\n" : "")
                        + "uk.co.enderfall.sdk.runtime.blockentity.nativebridge.PortableItemRenderer.register(binding, spec);"
                : (StandaloneModelRendererSources.supports(policy)
                        ? "uk.co.enderfall.sdk.runtime.blockentity.nativebridge.PortableModelRenderer.register(modBus, spec);\n" : "")
                        + "uk.co.enderfall.sdk.runtime.blockentity.nativebridge.PortableItemRenderer.register(modBus, binding, spec);");
    }

    static String emit(BlockEntityNativePolicy policy) {
        String register = policy.fabric()
                ? "public static void register(StoredBlockEntity.Binding binding, BlockEntityRenderSpec spec) {\n"
                    + "        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(binding.type(), context -> new PortableItemRenderer(spec));\n    }"
                : "public static void register(" + (policy.legacy() ? "net.minecraftforge.eventbus.api.IEventBus" : "net.neoforged.bus.api.IEventBus")
                    + " bus, StoredBlockEntity.Binding binding, BlockEntityRenderSpec spec) {\n"
                    + "        bus.addListener((" + (policy.legacy() ? "net.minecraftforge" : "net.neoforged.neoforge")
                    + ".client.event.EntityRenderersEvent.RegisterRenderers event) ->\n"
                    + "                event.registerBlockEntityRenderer(binding.type(), context -> new PortableItemRenderer(spec)));\n    }";
        if (policy.unobfuscated()) return ExtractedItemRendererSources.emit(register.replace(
                "new PortableItemRenderer(spec)", "new PortableItemRenderer(spec, context.itemModelResolver())"), true);
        return """
                package uk.co.enderfall.sdk.runtime.blockentity.nativebridge;

                import com.mojang.blaze3d.vertex.PoseStack;
                import com.mojang.math.Axis;
                import net.minecraft.client.Minecraft;
                import net.minecraft.client.renderer.MultiBufferSource;
                import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
                import net.minecraft.core.registries.BuiltInRegistries;
                import net.minecraft.resources.ResourceLocation;
                import net.minecraft.world.item.ItemDisplayContext;
                import net.minecraft.world.item.ItemStack;
                import uk.co.enderfall.sdk.api.render.BlockEntityRenderSpec;

                /** Generated client-only fixed-item renderer. No server storage reads. */
                public final class PortableItemRenderer implements BlockEntityRenderer<StoredBlockEntity> {
                    private final BlockEntityRenderSpec spec;
                    private PortableItemRenderer(BlockEntityRenderSpec spec) { this.spec = spec; }
                    ${REGISTER}

                    @Override
                    public void render(StoredBlockEntity entity, float partialTick, PoseStack pose,
                            MultiBufferSource buffers, int light, int overlay) {
                        var level = entity.getLevel();
                        if (level == null || !level.isClientSide || entity.isRemoved()) return;
                        ${MODEL_RENDER}
                        for (var display : spec.fluids()) {
                            PortableFluidRenderer.render(entity, display, pose, buffers, light, overlay);
                        }
                        for (var display : spec.items()) {
                            ItemStack stack;
                            if (display.source() instanceof BlockEntityRenderSpec.InventorySlot slot) {
                                stack = entity.renderStack(slot.slot());
                            } else {
                                var source = (BlockEntityRenderSpec.FixedItem) display.source();
                                var id = ${ID};
                                var nativeItem = ${ITEM_LOOKUP};
                                if (nativeItem == null) continue;
                                stack = new ItemStack(nativeItem);
                            }
                            if (stack.isEmpty()) continue;
                            var transform = display.sample(level.getGameTime() + (double) partialTick);
                            var lightPos = entity.getBlockPos().offset((int) Math.floor(transform.x()),
                                    (int) Math.floor(transform.y()), (int) Math.floor(transform.z()));
                            int displayLight = level.getChunkSource().hasChunk(lightPos.getX() >> 4, lightPos.getZ() >> 4)
                                    ? net.minecraft.client.renderer.LevelRenderer.getLightColor(level, lightPos) : light;
                            pose.pushPose();
                            try {
                                pose.translate(transform.x(), transform.y(), transform.z());
                                pose.translate(transform.pivotX(), transform.pivotY(), transform.pivotZ());
                                pose.mulPose(Axis.XP.rotationDegrees(transform.rotationX()));
                                pose.mulPose(Axis.YP.rotationDegrees(transform.rotationY()));
                                pose.mulPose(Axis.ZP.rotationDegrees(transform.rotationZ()));
                                pose.scale(transform.scale(), transform.scale(), transform.scale());
                                pose.translate(-transform.pivotX(), -transform.pivotY(), -transform.pivotZ());
                                boolean leftHand = display.pose() == uk.co.enderfall.sdk.api.render.ItemRenderPose.FIRST_PERSON_LEFT_HAND
                                        || display.pose() == uk.co.enderfall.sdk.api.render.ItemRenderPose.THIRD_PERSON_LEFT_HAND;
                                Minecraft.getInstance().getItemRenderer().renderStatic(null, stack, PortableItemPose.resolve(display.pose()),
                                        leftHand, pose, buffers, level, displayLight, overlay, entity.getBlockPos().hashCode());
                            } finally {
                                pose.popPose();
                            }
                        }
                    }
                }
                """.replace("${REGISTER}", register)
                .replace("${MODEL_RENDER}", StandaloneModelRendererSources.supports(policy)
                        ? "for (var display : spec.models()) PortableModelRenderer.render(entity, display, pose, buffers, light, overlay, partialTick);" : "")
                .replace("${ITEM_LOOKUP}", policy.legacy() && !policy.fabric()
                        ? "net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id)"
                        : policy.modernRecipes() ? "BuiltInRegistries.ITEM.getValue(id)" : "BuiltInRegistries.ITEM.get(id)")
                .replace("${ID}", policy.legacy()
                    ? "java.util.Objects.requireNonNull(ResourceLocation.tryParse(source.item().id().toString()))"
                    : "ResourceLocation.parse(source.item().id().toString())");
    }
}
