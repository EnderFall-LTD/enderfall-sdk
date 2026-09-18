package uk.co.enderfall.sdk.api.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.item.TooltipColor;
import uk.co.enderfall.sdk.api.item.TooltipVisibility;

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

    @Test
    void portableTooltipConveniencesPreserveOrderAndVisibility() {
        ItemSpec spec = ItemSpec.builder()
                .tooltip("tooltip.example.summary")
                .shiftHint("tooltip.example.hold_shift")
                .shiftTooltip("tooltip.example.details")
                .build();

        assertEquals(3, spec.tooltipLines().size());
        assertEquals(TooltipVisibility.ALWAYS, spec.tooltipLines().get(0).visibility());
        assertEquals(TooltipVisibility.SHIFT_UP, spec.tooltipLines().get(1).visibility());
        assertEquals(TooltipColor.DARK_GRAY, spec.tooltipLines().get(1).color());
        assertEquals(TooltipVisibility.SHIFT_DOWN, spec.tooltipLines().get(2).visibility());
    }

    @Test
    void itemCopyIncludesTooltipLinesWithoutSharingBuilderState() {
        ItemSpec original = ItemSpec.builder().tooltip("tooltip.example.original").build();
        ItemSpec.Builder copy = ItemSpec.builder().copyFrom(original);
        ItemSpec changed = copy.tooltip("tooltip.example.additional").build();

        assertEquals(1, original.tooltipLines().size());
        assertEquals(2, changed.tooltipLines().size());
    }
}
