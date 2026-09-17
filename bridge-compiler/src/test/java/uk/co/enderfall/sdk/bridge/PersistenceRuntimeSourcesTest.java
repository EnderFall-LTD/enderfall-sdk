package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;

class PersistenceRuntimeSourcesTest {
    @TempDir Path directory;

    @Test void allTargetsUseOrdinaryConsumerBootstrapAndCompleteFeatureSources() throws Exception {
        for (String target : TargetCatalog.standard().targetIds()) {
            Path output = directory.resolve(target);
            var request = new GenerationRequest(target, Path.of("..", "bridge-runtime"),
                    output.resolve("java"), output.resolve("resources"), true);
            new BridgeCompiler().generate(request);
            try (var files = Files.walk(output.resolve("java"))) {
                var sources = files.filter(Files::isRegularFile).toList();
                for (String renderer : new String[] {"PortableModelRenderer.java", "PortableItemRenderer.java"}) {
                    String code = Files.readString(sources.stream()
                            .filter(path -> path.getFileName().toString().equals(renderer)).findFirst().orElseThrow());
                    int placement = code.indexOf("pose.translate(transform.x(), transform.y(), transform.z())");
                    int pivot = code.indexOf("pose.translate(transform.pivotX(), transform.pivotY(), transform.pivotZ())");
                    int rotation = code.indexOf("pose.mulPose(", pivot);
                    int scale = code.indexOf("pose.scale(transform.scale(), transform.scale(), transform.scale())", rotation);
                    int unpivot = code.indexOf("pose.translate(-transform.pivotX(), -transform.pivotY(), -transform.pivotZ())");
                    assertTrue(placement >= 0 && pivot > placement && rotation > pivot
                            && scale > rotation && unpivot > scale, target + " " + renderer);
                }
                Path bootstrap = sources.stream().filter(path -> path.getFileName().toString().endsWith("ConsumerBootstrap.java")).findFirst().orElseThrow();
                String source = Files.readString(bootstrap);
                assertTrue(source.contains("PersistentPlatformAdapter adapter = new"), target);
                assertFalse(source.contains("PersistentPreviewBootstrap"), target);
                assertTrue(source.contains("adapter.enableMenuGauges()"), target);
                Path platform = sources.stream().filter(path -> path.getFileName().toString().endsWith("PersistentPlatformAdapter.java")).findFirst().orElseThrow();
                String platformSource = Files.readString(platform);
                assertTrue(platformSource.contains("supportsBlockShapes() { return true; }"), target);
                assertTrue(platformSource.contains("uk.co.enderfall.sdk.runtime.blockentity.nativebridge.PortableShapeBlock.create("), target);
                String shapeSource = Files.readString(sources.stream().filter(path -> path.getFileName().toString().equals("PortableShapeBlock.java")).findFirst().orElseThrow());
                assertTrue(shapeSource.contains("specification.behavior().orElseThrow().onPlace(portableContext)"), target);
                assertTrue(shapeSource.contains("new uk.co.enderfall.sdk.api.block.BlockPlacementContext("), target);
                assertTrue(shapeSource.contains("onNeighborUpdate(context)"), target);
                boolean legacyNeighbor = target.startsWith("1.20.1-") || target.startsWith("1.21.1-");
                assertEquals(legacyNeighbor, shapeSource.contains("LevelAccessor level, BlockPos pos"), target);
                assertEquals(!legacyNeighbor, shapeSource.contains("ScheduledTickAccess scheduledTicks"), target);
                assertTrue(shapeSource.contains("getCollisionShape("), target);
                assertTrue(shapeSource.contains("portableState(BlockState state)"), target);
                assertTrue(shapeSource.contains("withPortableState(BlockState nativeState"), target);
                assertTrue(shapeSource.contains("stateDefinition.getPossibleStates()"), target);
                assertTrue(shapeSource.contains("stateShapes = java.util.Map.copyOf(dynamic)"), target);
                assertTrue(shapeSource.contains("shapes.shape(spec.states().parse(encoded))"), target);
                assertTrue(platformSource.contains("supportsStateShapes() { return true; }"), target);
                assertTrue(platformSource.contains("Capability.BLOCK_STATES"), target);
                assertTrue(platformSource.contains("blockState("), target);
                assertTrue(platformSource.contains("updateBlockState("), target);
                assertTrue(platformSource.contains("!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)"), target);
                assertTrue(platformSource.contains("current.isSameThread()"), target);
                assertTrue(shapeSource.contains("CONSTRUCTION.remove()"), target);
                assertTrue(shapeSource.contains("CONSTRUCTION.set(previous)"), target);
                assertTrue(shapeSource.contains("builder.add(new PortableProperty(property))"), target);
                assertTrue(shapeSource.contains("specification.states().defaultState().serializedValues()"), target);
                assertTrue(shapeSource.contains("getInternalIndex(String value)"), target);
                assertTrue(platformSource.contains("supportsBlockStates() { return true; }"), target);
                assertTrue(platformSource.contains("supportsHorizontalFacing() { return true; }"), target);
                assertTrue(shapeSource.contains("if (this instanceof Directional) builder.add(facingProperty())"), target);
                assertTrue(shapeSource.contains("context.getHorizontalDirection().getOpposite()"), target);
                assertTrue(shapeSource.contains("rotation.rotate(state.getValue(facingProperty()))"), target);
                assertTrue(shapeSource.contains("mirror.mirror(state.getValue(facingProperty()))"), target);
                assertTrue(shapeSource.contains("context.getNearestLookingDirection().getOpposite()"), target);
                assertTrue(shapeSource.contains("convert(shape.rotateX(1)), convert(shape.rotateX(-1))"), target);
                assertTrue(shapeSource.contains("outline[directionIndex(state)]"), target);
                assertTrue(shapeSource.contains("getShape("), target);
                assertTrue(shapeSource.contains("Shapes.box(box.minX() / 16.0"), target);
                String shapedStorageSource = Files.readString(sources.stream().filter(path -> path.getFileName().toString().equals("StoredBlockEntity.java")).findFirst().orElseThrow());
                assertTrue(shapedStorageSource.contains("StoredBlock extends PortableShapeBlock"), target);
                assertTrue(shapedStorageSource.contains("PortableShapeBlock.construct(binding.blockSpec"), target);
                assertTrue(shapedStorageSource.contains("super(properties, binding.blockSpec)"), target);
                assertTrue(shapedStorageSource.contains("DirectionalStoredBlock extends StoredBlock implements PortableShapeBlock.Directional"), target);
                assertTrue(shapedStorageSource.contains("SixWayStoredBlock extends StoredBlock implements PortableShapeBlock.SixWayDirectional"), target);
                assertTrue(platformSource.contains("supportsBlockPropertyCopy() { return true; }"), target);
                assertTrue(platformSource.contains("properties = copiedBlockProperties(spec)"), target);
                assertTrue(platformSource.contains("Missing block property source"), target);
                assertTrue(platformSource.contains("spec.overrides(BlockSpec.Property.LUMINANCE)"), target);
                assertTrue(platformSource.contains("void registerBlockEntityRenderer("), target);
                var modelRenderer = sources.stream().filter(path -> path.getFileName().toString().equals("PortableModelRenderer.java")).findFirst();
                assertTrue(modelRenderer.isPresent(), target);
                if (modelRenderer.isPresent()) {
                    String modelSource = Files.readString(modelRenderer.get());
                    assertTrue(modelSource.contains("getModelManager()"), target);
                    assertTrue(modelSource.contains("getGameTime(), partialTick"), target);
                    assertTrue(modelSource.contains("entity.renderAnimation()"), target);
                    assertTrue(modelSource.contains("display.sample("), target);
                    if (target.startsWith("26.2-")) {
                        assertTrue(modelSource.contains(target.endsWith("fabric")
                                ? "SimpleUnbakedExtraModel.blockStateModel(id)" : "SimpleUnbakedStandaloneModel.blockStateModel(id)"), target);
                        String submit = modelSource.substring(modelSource.indexOf("static void submit("));
                        assertFalse(submit.contains("entity."), target);
                        assertFalse(submit.contains("display.sample("), target);
                        assertFalse(submit.contains("getGameTime()"), target);
                        assertFalse(submit.contains("Minecraft.getInstance()"), target);
                        assertTrue(submit.contains("submitBlockModel"), target);
                    } else if (target.endsWith("-fabric")) {
                        assertTrue(modelSource.contains("ModelLoadingPlugin.register(context -> context.addModels(ids))"), target);
                        assertTrue(modelSource.contains("FabricBakedModelManager) manager).getModel(id)"), target);
                        assertFalse(modelSource.contains("net.neoforged"), target);
                        assertFalse(modelSource.contains("net.minecraftforge"), target);
                    } else if (target.startsWith("1.20.1-")) {
                        assertTrue(modelSource.contains("getModel(id)"), target);
                        assertTrue(modelSource.contains("net.minecraftforge.client.event.ModelEvent.RegisterAdditional"), target);
                    } else if (target.startsWith("1.21.1-")) {
                        assertTrue(modelSource.contains("event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(id))"), target);
                    } else {
                        assertTrue(modelSource.contains("getStandaloneModel(id)"), target);
                    }
                    assertTrue(modelSource.contains("finally { pose.popPose(); }"), target);
                    assertFalse(modelSource.contains("new ItemStack"), target);
                } else {
                    assertTrue(platformSource.contains("if (!spec.models().isEmpty()) throw new UnsupportedOperationException"), target);
                }
                assertTrue(platformSource.contains("binding.definition().renderTanks().contains(display.tank().name())"), target);
                var fluidRenderer = sources.stream().filter(path -> path.getFileName().toString().equals("PortableFluidRenderer.java")).findFirst();
                assertTrue(fluidRenderer.isPresent(), target);
                if (fluidRenderer.isPresent()) {
                    String fluidSource = Files.readString(fluidRenderer.get());
                    assertTrue(fluidSource.contains("entity.renderTank(display.tank().name())"), target);
                    assertTrue(fluidSource.contains("entityTranslucent("), target);
                    if (target.startsWith("26.2-")) {
                        String submit = fluidSource.substring(fluidSource.indexOf("static void submit("));
                        assertFalse(submit.contains("entity."), target);
                        assertFalse(submit.contains("Minecraft.getInstance()"), target);
                        assertTrue(fluidSource.contains("model.tintSource() == null ? -1"), target);
                        assertTrue(fluidSource.contains("instanceof net.minecraft.client.multiplayer.ClientLevel"), target);
                    }
                    assertFalse(fluidSource.contains("storage.tank("), target);
                    assertTrue(fluidSource.contains("FluidCuboidMesh.create("), target);
                }
                var itemRenderer = sources.stream().filter(path -> path.getFileName().toString().equals("PortableItemRenderer.java")).findFirst();
                assertTrue(itemRenderer.isPresent(), target);
                if (itemRenderer.isPresent()) {
                    String renderer = Files.readString(itemRenderer.get());
                    assertTrue(renderer.contains("finally {"), target);
                    assertTrue(renderer.contains("pose.popPose();"), target);
                    assertFalse(renderer.contains("entity.inventory()"), target);
                    assertTrue(platformSource.contains("binding.definition().renderSlots().contains(slot.slot())"), target);
                    assertTrue(renderer.contains("entity.renderStack(slot.slot())"), target);
                    assertTrue(renderer.contains("Math.floor(transform.y())"), target);
                    assertTrue(renderer.contains("hasChunk(lightPos.getX() >> 4"), target);
                    if (target.startsWith("26.2-")) {
                        String submit = renderer.substring(renderer.indexOf("void submit("));
                        assertFalse(submit.contains("entity."), target);
                        assertTrue(renderer.contains("state.displays.clear()"), target);
                    }
                }
                Path storage = sources.stream().filter(path -> path.getFileName().toString().equals("StoredBlockEntity.java")).findFirst().orElseThrow();
                String storedSource = Files.readString(storage);
                assertTrue(storedSource.contains("notifyRenderSnapshot();"), target);
                int updateStart = storedSource.indexOf("public CompoundTag getUpdateTag(");
                assertTrue(updateStart >= 0, target);
                String update = storedSource.substring(updateStart, storedSource.indexOf("getUpdatePacket()", updateStart));
                assertFalse(update.contains("storage.save()"), target);
                assertFalse(update.contains("PROCESS_KEY"), target);
                assertTrue(update.contains("tag.putByteArray(RENDER_KEY, payload)"), target);
                assertTrue(update.contains("tag.putByteArray(RENDER_TANK_KEY, tankPayload)"), target);
                assertTrue(storedSource.contains("RenderTankSnapshot.decode(tanks, definition)"), target);
                assertTrue(storedSource.indexOf("var tankReplacement =") < storedSource.indexOf("renderSnapshot = itemReplacement"), target);
                assertTrue(platformSource.contains("void bindTankMenu("), target);
                assertTrue(platformSource.contains("level.getBlockEntity(pos) != owner"), target);
                assertTrue(platformSource.contains("level.getChunkSource().hasChunk("), target);
                Path screen = sources.stream().filter(path -> path.getFileName().toString().endsWith("PortableMenuScreen.java")).findFirst().orElseThrow();
                assertTrue(Files.readString(screen).contains("gauge.filledPixels(view.state())"), target);
                String screenSource = Files.readString(screen);
                boolean needsEarlyBackground = target.startsWith("1.21.1-") || target.startsWith("1.21.4-");
                String earlyBackground = "super.renderBackground(graphics, mouseX, mouseY, partialTick);";
                assertEquals(needsEarlyBackground, screenSource.contains(earlyBackground), target);
                if (needsEarlyBackground) {
                    assertTrue(screenSource.indexOf(earlyBackground) < screenSource.indexOf("graphics.fill(0, 0"), target);
                    assertTrue(screenSource.contains("public void renderBackground(GuiGraphics"), target);
                    assertTrue(screenSource.indexOf("gauge.filledPixels") < screenSource.indexOf("super.render(graphics"), target);
                }
                assertEquals(1, sources.stream().filter(path -> path.getFileName().toString().endsWith("TimedWorkbenchProcessor.java")).count());
                assertEquals(1, sources.stream().filter(path -> path.getFileName().toString().equals("StoredBlockEntity.java")).count());
                for (Path file : sources) assertFalse(Files.readString(file).contains("${"), file.toString());
            }
        }
    }
}
