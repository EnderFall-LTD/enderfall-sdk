package uk.co.enderfall.sdk.api.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.registry.ItemRef;

class BlockEntityRenderSpecTest {
    @Test void modelPlansAreBoundedImmutableAndDistinctFromItemIds() {
        var model = new ModelRef(ResourceId.of("test_mod", "block/machine/lid"));
        var transform = RenderTransform.at(0, 0, 0);
        var builder = BlockEntityRenderSpec.builder().model(model, transform);
        var first = builder.build();
        assertEquals(model, first.models().get(0).model());
        assertTrue(first.items().isEmpty());
        for (int i = 1; i < 64; i++) builder.model(model, transform);
        assertEquals(1, first.models().size());
        assertThrows(UnsupportedOperationException.class, () -> first.models().clear());
        assertThrows(IllegalArgumentException.class, () -> builder.model(model, transform));
        assertThrows(NullPointerException.class, () -> new ModelRef(null));
        assertThrows(NullPointerException.class, () -> BlockEntityRenderSpec.builder().model(model, null));
        assertThrows(IllegalArgumentException.class, () -> new ModelRef(ResourceId.of("test_mod", "models/block/lid")));
        assertThrows(IllegalArgumentException.class, () -> new ModelRef(ResourceId.of("test_mod", "block/lid.json")));
    }
    @Test void supportsExplicitPosesAndPreservesFixedDefault() {
        var transform = RenderTransform.at(.5f, 1, .5f);
        var item = new ItemRef(ResourceId.of("minecraft", "apple"));
        assertEquals(ItemRenderPose.FIXED, BlockEntityRenderSpec.builder().item(item, transform).build().items().get(0).pose());
        assertEquals(ItemRenderPose.FIXED, new BlockEntityRenderSpec.ItemDisplay(new BlockEntityRenderSpec.FixedItem(item), transform).pose());
        for (var pose : ItemRenderPose.values()) {
            var plan = BlockEntityRenderSpec.builder().item(item, transform, pose).inventorySlot(0, transform, pose).build();
            assertEquals(pose, plan.items().get(0).pose());
            assertEquals(pose, plan.items().get(1).pose());
        }
        assertThrows(NullPointerException.class, () -> BlockEntityRenderSpec.builder().item(item, transform, null));
    }
    @Test void buildsImmutableDisplayPlan() {
        var transform = RenderTransform.at(0.5f, 1.02f, 0.5f).rotate(90, 0, 0).scaled(0.35f);
        var builder = BlockEntityRenderSpec.builder().inventorySlot(0, transform);
        var plan = builder.build();
        builder.item(new ItemRef(ResourceId.of("minecraft", "apple")), transform);
        assertEquals(1, plan.items().size());
        assertEquals(2, builder.build().items().size());
        assertEquals(new BlockEntityRenderSpec.InventorySlot(0), plan.items().get(0).source());
        assertEquals(0.35f, plan.items().get(0).transform().scale());
        assertThrows(UnsupportedOperationException.class, () -> plan.items().clear());
    }

    @Test void rejectsInvalidTransformsAndUnboundedPlans() {
        assertThrows(IllegalArgumentException.class, () -> RenderTransform.at(Float.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> RenderTransform.at(17, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> RenderTransform.at(0, 0, 0).rotate(Float.POSITIVE_INFINITY, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> RenderTransform.at(0, 0, 0).scaled(0));
        assertThrows(IllegalArgumentException.class, () -> BlockEntityRenderSpec.builder().build());
        assertThrows(IllegalArgumentException.class, () -> new BlockEntityRenderSpec.InventorySlot(-1));
        assertThrows(IllegalArgumentException.class, () -> new BlockEntityRenderSpec.InventorySlot(256));
        var builder = BlockEntityRenderSpec.builder();
        for (int i = 0; i < 64; i++) builder.inventorySlot(0, RenderTransform.at(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> builder.inventorySlot(0, RenderTransform.at(0, 0, 0)));
        assertEquals(64, builder.build().items().size());
    }
}
