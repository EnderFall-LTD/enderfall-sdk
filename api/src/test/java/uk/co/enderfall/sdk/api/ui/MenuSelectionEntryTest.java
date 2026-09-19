package uk.co.enderfall.sdk.api.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.registry.ItemRef;

class MenuSelectionEntryTest {
    @Test
    void addsImmutableBoundedItemPresentation() {
        var entry = MenuSelectionEntry.enabled("test:chair", "Oak chair")
                .item(new ItemRef(ResourceId.of("minecraft", "oak_planks")), 4)
                .details("Four planks");

        assertEquals("minecraft:oak_planks", entry.icon().orElseThrow().id().toString());
        assertEquals(4, entry.count());
        assertEquals("Four planks", entry.tooltip());
        assertTrue(entry.enabled());
    }

    @Test
    void rejectsUnsafePresentationBounds() {
        var item = new ItemRef(ResourceId.of("minecraft", "stone"));
        assertThrows(IllegalArgumentException.class,
                () -> MenuSelectionEntry.enabled("test:stone", "Stone").item(item, 0));
        assertThrows(IllegalArgumentException.class,
                () -> MenuSelectionEntry.enabled("test:stone", "Stone").details("bad\nline"));
    }
}
