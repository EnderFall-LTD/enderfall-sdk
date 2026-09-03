package uk.co.enderfall.sdk.gradle.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.gradle.api.Action;

public final class TargetsDefinition {
    private final List<VersionDefinition> versions = new ArrayList<>();

    public void version(String minecraftVersion, Action<? super VersionDefinition> action) {
        VersionDefinition definition = new VersionDefinition(Objects.requireNonNull(minecraftVersion, "minecraftVersion"));
        action.execute(definition);
        versions.add(definition);
    }

    public List<VersionDefinition> versions() {
        return List.copyOf(versions);
    }
}
