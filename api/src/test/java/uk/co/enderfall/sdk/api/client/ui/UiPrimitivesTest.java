package uk.co.enderfall.sdk.api.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.item.ItemStackRef;
import uk.co.enderfall.sdk.api.registry.ItemRef;

class UiPrimitivesTest {
    @Test
    void rectanglesUseHalfOpenBoundsAndDeterministicIntersections() {
        UiRect bounds = new UiRect(10, 20, 30, 40);
        assertTrue(bounds.contains(10, 20));
        assertTrue(bounds.contains(39.999, 59.999));
        assertFalse(bounds.contains(40, 60));
        assertEquals(new UiRect(25, 30, 15, 10), bounds.intersect(new UiRect(25, 30, 20, 10)));
        assertEquals(new UiRect(100, 100, 0, 0), bounds.intersect(new UiRect(100, 100, 5, 5)));
    }

    @Test
    void rejectsUnsafeGeometryFramesAndTextPolicies() {
        assertThrows(IllegalArgumentException.class, () -> new UiRect(0, 0, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new UiInsets(-1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new UiRenderFrame(1920, 1080, 0, 0, Float.NaN, 1));
        UiRenderFrame arbitraryClockOrigin =
                new UiRenderFrame(1920, 1080, 0, 0, 0.5F, Long.MIN_VALUE);
        assertEquals(Long.MIN_VALUE, arbitraryClockOrigin.frameNanos());
        UiRenderFrame extractionDelta = new UiRenderFrame(1920, 1080, 0, 0, 20.0F, 1);
        assertEquals(20.0F, extractionDelta.partialTick());
        assertThrows(IllegalArgumentException.class, () -> ClientScreenSpec.of("two\nlines"));
        assertEquals(new UiInsets(4, 4, 4, 4), UiInsets.uniform(4));
        ItemRef item = new ItemRef(ResourceId.of("minecraft", "diamond"));
        assertEquals(64, ItemStackRef.of(item, 64).count());
        assertThrows(IllegalArgumentException.class, () -> ItemStackRef.of(item, 0));
    }
}
