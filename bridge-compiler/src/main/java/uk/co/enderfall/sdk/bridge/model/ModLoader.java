package uk.co.enderfall.sdk.bridge.model;

/** Mod loaders for which EnderFall can build a target artifact. */
public enum ModLoader {
    FABRIC("fabric"),
    FORGE("forge"),
    NEOFORGE("neoforge");

    private final String id;

    ModLoader(String id) {
        this.id = id;
    }

    /** Returns the stable lowercase loader identifier used in target IDs. */
    public String id() {
        return id;
    }
}
