package uk.co.enderfall.sdk.api.gameplay;

import java.util.Objects;

/**
 * A loader-neutral location in a player's carried inventory.
 *
 * <p>The indices are relative to their named area, so portable code never relies on
 * Minecraft's internal combined-container slot numbers.</p>
 */
public record PlayerInventorySlot(Area area, int index) {
    public PlayerInventorySlot {
        Objects.requireNonNull(area, "area");
        int maximum = switch (area) {
            case HOTBAR -> 8;
            case MAIN -> 26;
            case OFF_HAND -> 0;
        };
        if (index < 0 || index > maximum) {
            throw new IllegalArgumentException(area + " slot index must be between 0 and " + maximum);
        }
    }

    public static PlayerInventorySlot hotbar(int index) {
        return new PlayerInventorySlot(Area.HOTBAR, index);
    }

    public static PlayerInventorySlot main(int index) {
        return new PlayerInventorySlot(Area.MAIN, index);
    }

    public static PlayerInventorySlot offHand() {
        return new PlayerInventorySlot(Area.OFF_HAND, 0);
    }

    /** Converts the stable vanilla carried-inventory range 0..35 without accepting armor slots. */
    public static PlayerInventorySlot carried(int index) {
        if (index < 0 || index > 35) {
            throw new IllegalArgumentException("Carried inventory slot must be between 0 and 35");
        }
        return index < 9 ? hotbar(index) : main(index - 9);
    }

    public enum Area {
        HOTBAR,
        MAIN,
        OFF_HAND
    }
}
