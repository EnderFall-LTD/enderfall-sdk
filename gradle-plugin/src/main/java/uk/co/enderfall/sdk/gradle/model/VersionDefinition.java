package uk.co.enderfall.sdk.gradle.model;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public final class VersionDefinition {
    private final String minecraftVersion;
    private final Set<String> loaders = new LinkedHashSet<>();

    VersionDefinition(String minecraftVersion) {
        this.minecraftVersion = minecraftVersion;
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    public void loaders(String... values) {
        Arrays.stream(values).map(String::toLowerCase).forEach(loaders::add);
    }

    public Set<String> loaderIds() {
        return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(loaders));
    }
}
