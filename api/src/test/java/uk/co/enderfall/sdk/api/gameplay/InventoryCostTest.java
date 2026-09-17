package uk.co.enderfall.sdk.api.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.registry.ItemRef;

class InventoryCostTest {
    private static final ItemRef CRYSTAL = new ItemRef(ResourceId.of("test", "crystal"));

    @Test
    void mergesRepeatedItemsIntoOneAtomicRequirement() {
        InventoryCost cost = InventoryCost.builder().item(CRYSTAL, 2).item(CRYSTAL, 3).build();

        assertEquals(5, cost.items().get(CRYSTAL.id()));
        assertThrows(UnsupportedOperationException.class,
                () -> cost.items().put(ResourceId.of("test", "other"), 1));
    }

    @Test
    void rejectsEmptyAndNonPositiveRequirements() {
        assertThrows(IllegalArgumentException.class, () -> InventoryCost.builder().build());
        assertThrows(IllegalArgumentException.class, () -> InventoryCost.of(CRYSTAL, 0));
    }
}
