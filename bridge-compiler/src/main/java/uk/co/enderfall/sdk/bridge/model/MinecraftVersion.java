package uk.co.enderfall.sdk.bridge.model;

/** Minecraft versions in the reviewed EnderFall target catalog. */
public enum MinecraftVersion {
    V1_20_1("1.20.1", 17),
    V1_21_1("1.21.1", 21),
    V1_21_4("1.21.4", 21),
    V26_2("26.2", 25);

    private final String id;
    private final int javaVersion;

    MinecraftVersion(String id, int javaVersion) {
        this.id = id;
        this.javaVersion = javaVersion;
    }

    /** Returns the exact Minecraft version string. */
    public String id() {
        return id;
    }

    /** Returns the Java toolchain version required for this Minecraft version. */
    public int javaVersion() {
        return javaVersion;
    }
}
