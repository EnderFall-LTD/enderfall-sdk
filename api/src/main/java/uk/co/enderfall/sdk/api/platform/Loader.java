package uk.co.enderfall.sdk.api.platform;

import java.util.Arrays;

/** Supported mod loaders. */
public enum Loader {
    FABRIC("fabric"),
    FORGE("forge"),
    NEOFORGE("neoforge");

    private final String id;

    Loader(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Loader fromId(String id) {
        return Arrays.stream(values())
                .filter(loader -> loader.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported loader: " + id));
    }
}
