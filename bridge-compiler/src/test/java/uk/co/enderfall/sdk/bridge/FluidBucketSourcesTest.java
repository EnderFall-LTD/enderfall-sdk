package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;

class FluidBucketSourcesTest {
    @Test void hooksUseReviewedResultTypeForEveryTarget() {
        for (String target : TargetCatalog.standard().targetIds()) {
            var policy = BlockEntityNativePolicy.require(target);
            String hook = FluidBucketSources.itemHook(policy);
            if (policy.legacy()) assertEquals("", hook);
            else assertTrue(hook.contains(target.startsWith("1.21.1-")
                    ? "net.minecraft.world.ItemInteractionResult useItemOn"
                    : "net.minecraft.world.InteractionResult useItemOn"), target);
        }
    }

    @Test void nativeExchangeRequiresWholeBucketAndUsesPortPolicy() {
        String source = FluidBucketSources.helper();
        assertTrue(source.contains("owner.fluidPort(spec, hit.getDirection())"));
        assertFalse(source.contains("held.getCount() != 1"));
        assertTrue(source.indexOf("tank.fill(volume, true) != bucket") < source.indexOf("tank.fill(volume, false)"));
        assertTrue(source.indexOf("bucket, true) != bucket") < source.indexOf("bucket, false)"));
        assertTrue(source.contains("ItemUtils.createFilledResult("));
        assertTrue(source.contains("empty ? \"Removed one bucket\" : \"Added one bucket\""));
        assertTrue(source.contains("No transfer: check fluid, space and face access"));
        assertTrue(source.contains("serverPlayer.sendSystemMessage("));
        assertTrue(source.contains("text.length() > 512"));
    }
}
