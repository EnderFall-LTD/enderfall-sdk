package uk.co.enderfall.sdk.api.ui;

/** Server-enforced behaviour of a portable inventory slot. */
public enum InventorySlotRole {
    /** Ordinary two-way storage. */
    STORAGE,
    /** Player insertion is allowed, optionally restricted by an item filter. */
    INPUT,
    /** Player insertion is denied; server processing may still populate the slot. */
    OUTPUT
}
