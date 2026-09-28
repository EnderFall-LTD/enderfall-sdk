package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;

class PortableInventoryScreenSourcesTest {
    @Test void emitsPortablePresentationLifecycleAndSynchronizedDataForEveryTarget() {
        for (var target : TargetCatalog.standard().targets()) {
            List<RuntimeSource> sources = PortableInventoryMenuSources.emit(
                    BlockEntityNativePolicy.require(target.id()));
            assertEquals(3, sources.size(), target.id());
            String combined = sources.stream()
                    .map(source -> new String(source.content(), StandardCharsets.UTF_8))
                    .reduce("", (left, right) -> left + right);
            assertTrue(combined.contains("registerPortableView"), target.id());
            assertTrue(combined.contains("renderBackground(context, portableContext)"), target.id());
            assertTrue(combined.contains("renderForeground(context, portableContext)"), target.id());
            assertTrue(combined.contains("portable.mouseClicked"), target.id());
            assertTrue(combined.contains("portable.characterTyped"), target.id());
            assertTrue(combined.contains("synchronizedValue(BlockEntityInt field)"), target.id());
            assertTrue(combined.contains("& 0xFFFF"), target.id());
        }
    }
}
