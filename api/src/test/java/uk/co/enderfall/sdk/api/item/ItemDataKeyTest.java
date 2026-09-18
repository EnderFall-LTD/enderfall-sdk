package uk.co.enderfall.sdk.api.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.registry.ItemSpec;

class ItemDataKeyTest {
    @Test
    void stringLimitsUtf8BytesRatherThanUtf16CodeUnits() {
        ItemDataKey<String> key = ItemDataKey.string(ResourceId.parse("example:letter_text"), 4);
        assertEquals("test", key.validate("test"));
        assertEquals("😀", key.validate("😀"));
        assertThrows(IllegalArgumentException.class, () -> key.validate("😀a"));
    }

    @Test
    void numericKeysKeepExactLongBounds() {
        long minimum = Long.MAX_VALUE - 1;
        ItemDataKey<Long> key = ItemDataKey.longInteger(ResourceId.parse("example:sequence"),
                minimum, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, key.validate(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> key.validate(minimum - 1));
    }

    @Test
    void itemSpecsRejectDuplicateDataIdsEvenWithDifferentTypes() {
        ResourceId id = ResourceId.parse("example:value");
        var builder = ItemSpec.builder().data(ItemDataKey.string(id, 64));
        assertThrows(IllegalArgumentException.class,
                () -> builder.data(ItemDataKey.integer(id, 0, 10)));
    }
}
