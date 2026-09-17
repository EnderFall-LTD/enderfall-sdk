package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Complete development feature composition using the ordinary consumer bootstrap. */
final class PersistenceRuntimeSources {
    private PersistenceRuntimeSources() { }

    static List<RuntimeSource> emit(String target) {
        var policy = BlockEntityNativePolicy.require(target);
        String root = "uk/co/enderfall/sdk/runtime/" + policy.runtimePackage().replace('.', '/') + "/" + policy.prefix();
        String menu = policy.legacy() || policy.unobfuscated()
                ? ContainerWorkbenchMenuEmitter.persistentPreview(policy.fabric(), policy.legacy())
                : WorkbenchMenuEmitter.persistentPreview(policy.modernRecipes(), policy.fabric());
        var sources = new java.util.ArrayList<>(List.of(
                source("uk/co/enderfall/sdk/runtime/blockentity/nativebridge/PortableShapeBlock.java", BlockShapeSources.emit(target)),
                source("uk/co/enderfall/sdk/runtime/blockentity/nativebridge/StoredBlockEntity.java", BlockEntityPreviewSources.emit(target)),
                source(root + "PersistentWorkbenchMenu.java", menu),
                source(root + "TimedWorkbenchProcessor.java", TimedWorkbenchProcessorSources.emit(policy)),
                source(root + "TimedWorkbenchMenu.java", TimedWorkbenchMenuSources.menu(policy)),
                source(root + "TimedWorkbenchClient.java", TimedWorkbenchMenuSources.client(policy))));
        sources.add(source(
                "uk/co/enderfall/sdk/runtime/blockentity/nativebridge/PortableItemRenderer.java",
                BlockEntityItemRendererSources.emit(policy)));
        sources.add(source(
                "uk/co/enderfall/sdk/runtime/blockentity/nativebridge/PortableFluidRenderer.java",
                policy.unobfuscated() ? ExtractedFluidRendererSources.emit() : ImmediateFluidRendererSources.emit(policy)));
        sources.add(source("uk/co/enderfall/sdk/runtime/blockentity/nativebridge/PortableItemPose.java", """
                package uk.co.enderfall.sdk.runtime.blockentity.nativebridge;
                import net.minecraft.world.item.ItemDisplayContext;
                import uk.co.enderfall.sdk.api.render.ItemRenderPose;
                final class PortableItemPose {
                    private PortableItemPose() { }
                    static ItemDisplayContext resolve(ItemRenderPose pose) {
                        return switch (pose) {
                            case NONE -> ItemDisplayContext.NONE;
                            case FIXED -> ItemDisplayContext.FIXED;
                            case GROUND -> ItemDisplayContext.GROUND;
                            case GUI -> ItemDisplayContext.GUI;
                            case HEAD -> ItemDisplayContext.HEAD;
                            case FIRST_PERSON_LEFT_HAND -> ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
                            case FIRST_PERSON_RIGHT_HAND -> ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
                            case THIRD_PERSON_LEFT_HAND -> ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
                            case THIRD_PERSON_RIGHT_HAND -> ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
                        };
                    }
                }
                """));
        if (StandaloneModelRendererSources.supports(policy)) sources.add(source(
                "uk/co/enderfall/sdk/runtime/blockentity/nativebridge/PortableModelRenderer.java", StandaloneModelRendererSources.emit(policy)));
        return List.copyOf(sources);
    }

    private static RuntimeSource source(String path, String content) {
        return new RuntimeSource(path, path, content.getBytes(StandardCharsets.UTF_8));
    }
}
