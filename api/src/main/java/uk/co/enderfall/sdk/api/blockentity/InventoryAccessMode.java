package uk.co.enderfall.sdk.api.blockentity;

/** Operations that automation may perform through one face of a portable inventory. */
public enum InventoryAccessMode {
    NONE(false, false),
    INSERT(true, false),
    EXTRACT(false, true),
    BOTH(true, true);

    private final boolean insert;
    private final boolean extract;

    InventoryAccessMode(boolean insert, boolean extract) {
        this.insert = insert;
        this.extract = extract;
    }

    public boolean allowsInsert() { return insert; }
    public boolean allowsExtract() { return extract; }
}
