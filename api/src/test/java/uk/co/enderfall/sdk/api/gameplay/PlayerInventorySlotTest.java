package uk.co.enderfall.sdk.api.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PlayerInventorySlotTest {
    @Test
    void convertsCarriedSlotsWithoutExposingNativeOffhandOrArmorNumbers() {
        assertEquals(PlayerInventorySlot.hotbar(0), PlayerInventorySlot.carried(0));
        assertEquals(PlayerInventorySlot.hotbar(8), PlayerInventorySlot.carried(8));
        assertEquals(PlayerInventorySlot.main(0), PlayerInventorySlot.carried(9));
        assertEquals(PlayerInventorySlot.main(26), PlayerInventorySlot.carried(35));
        assertEquals(PlayerInventorySlot.offHand(), new PlayerInventorySlot(
                PlayerInventorySlot.Area.OFF_HAND, 0));
    }

    @Test
    void rejectsSlotsOutsideTheirLogicalArea() {
        assertThrows(IllegalArgumentException.class, () -> PlayerInventorySlot.hotbar(9));
        assertThrows(IllegalArgumentException.class, () -> PlayerInventorySlot.main(27));
        assertThrows(IllegalArgumentException.class, () -> PlayerInventorySlot.carried(40));
        assertThrows(IllegalArgumentException.class, () -> new PlayerInventorySlot(
                PlayerInventorySlot.Area.OFF_HAND, 1));
    }
}
