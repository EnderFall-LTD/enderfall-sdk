package uk.co.enderfall.sdk.preview;

import uk.co.enderfall.sdk.api.ClientModContext;
import uk.co.enderfall.sdk.api.EnderfallClientMod;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.registry.BlockRef;
import uk.co.enderfall.sdk.api.registry.ItemRef;
import uk.co.enderfall.sdk.api.render.BlockEntityRenderSpec;
import uk.co.enderfall.sdk.api.render.RenderTransform;

/** Portable client entrypoint; no native client classes or server-owned inventory reads. */
public final class PreviewRenderClient implements EnderfallClientMod {
    @Override public void initialize(ClientModContext context) {
        try {
            var closed = RenderTransform.at(0, 1.02f, 0).pivot(.5f, 0, 15f / 16f);
            var opened = closed.rotate(75, 0, 0);
            context.blockEntityRenderers().register(new BlockRef(context.id("timed_workbench")),
                    BlockEntityRenderSpec.builder().namedModel(
                            new uk.co.enderfall.sdk.api.render.ModelRef(context.id("block/display_lid")),
                            closed, java.util.Map.of(
                                    "open", new uk.co.enderfall.sdk.api.render.RenderAnimation(
                                            new uk.co.enderfall.sdk.api.render.RenderTransition(closed, opened, 10,
                                                    uk.co.enderfall.sdk.api.render.RenderTransition.Easing.SMOOTHSTEP),
                                            uk.co.enderfall.sdk.api.render.RenderAnimation.Playback.ONCE),
                                    "close", new uk.co.enderfall.sdk.api.render.RenderAnimation(
                                            new uk.co.enderfall.sdk.api.render.RenderTransition(opened, closed, 10,
                                                    uk.co.enderfall.sdk.api.render.RenderTransition.Easing.SMOOTHSTEP),
                                            uk.co.enderfall.sdk.api.render.RenderAnimation.Playback.ONCE))).build());
        } catch (UnsupportedOperationException unavailable) {
            context.logger().info("Standalone model drawing is not implemented on this development target yet.");
        }
        var display = BlockEntityRenderSpec.builder()
                .animatedItem(new ItemRef(ResourceId.of("minecraft", "apple")),
                        new uk.co.enderfall.sdk.api.render.RenderAnimation(
                                new uk.co.enderfall.sdk.api.render.RenderTransition(
                                        RenderTransform.at(.5f, 1.15f, .5f).rotate(90, 0, 0).scaled(.5f),
                                        RenderTransform.at(.5f, 1.45f, .5f).rotate(90, 0, 0).scaled(.5f),
                                        30, uk.co.enderfall.sdk.api.render.RenderTransition.Easing.SMOOTHSTEP)))
                .inventorySlot(0, RenderTransform.at(0.2f, 1.05f, 0.2f).rotate(90, 0, 0).scaled(0.25f))
                .inventorySlot(1, RenderTransform.at(0.5f, 1.05f, 0.2f).rotate(90, 0, 0).scaled(0.25f))
                .inventorySlot(2, RenderTransform.at(0.8f, 1.05f, 0.2f).rotate(90, 0, 0).scaled(0.25f))
                .build();
        try {
            context.blockEntityRenderers().register(new BlockRef(context.id("workbench")), display);
        } catch (UnsupportedOperationException unavailable) {
            context.logger().info("Fixed-item renderer is not implemented on this development target yet.");
        }
        try {
            context.blockEntityRenderers().register(new BlockRef(context.id("fluid_tank")),
                    BlockEntityRenderSpec.builder().fluid(PreviewFluids.RESERVOIR,
                            new uk.co.enderfall.sdk.api.render.FluidRenderBounds(.13, .13, .13, .87, .87, .87)).build());
        } catch (UnsupportedOperationException unavailable) {
            context.logger().info("Fluid drawing is not implemented on this development target yet; the tank menu remains available.");
        }
    }
}
