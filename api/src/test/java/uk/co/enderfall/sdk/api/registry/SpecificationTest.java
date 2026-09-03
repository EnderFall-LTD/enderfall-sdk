package uk.co.enderfall.sdk.api.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SpecificationTest {
    @Test
    void durableItemsMustBeUnstackable() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ItemSpec.builder().maxStackSize(16).durability(200).build());
        assertEquals("Durable items must have maxStackSize 1", error.getMessage());
    }

    @Test
    void blockLightLevelIsBounded() {
        assertThrows(IllegalArgumentException.class, () -> BlockSpec.builder().luminance(16));
    }
}
