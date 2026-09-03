package uk.co.enderfall.sdk.gradle.model;

import java.util.Objects;

public record TargetDefinition(String minecraftVersion, String loader, int javaVersion,
                               String loaderVersion, String platformApiVersion) {
    public TargetDefinition {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(loaderVersion, "loaderVersion");
        Objects.requireNonNull(platformApiVersion, "platformApiVersion");
    }

    public String id() {
        return minecraftVersion + '-' + loader;
    }

    public String projectName() {
        return id().replace('.', '_').replace('-', '_');
    }
}
