package uk.co.enderfall.sdk.gradle.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.gradle.api.Action;

/** Ordered dependency declarations used by every generated target project. */
public final class ModDependenciesDefinition {
    private final Map<String, ModDependencyDefinition> dependencies = new LinkedHashMap<>();

    public void required(String id, String version, Action<? super ModDependencyDefinition> action) {
        add(id, version, ModDependencyDefinition.Requirement.REQUIRED, action);
    }

    public void optional(String id, String version, Action<? super ModDependencyDefinition> action) {
        add(id, version, ModDependencyDefinition.Requirement.OPTIONAL, action);
    }

    public void required(String id, String version) {
        required(id, version, ignored -> { });
    }

    public void optional(String id, String version) {
        optional(id, version, ignored -> { });
    }

    public List<ModDependencyDefinition> all() {
        return List.copyOf(new ArrayList<>(dependencies.values()));
    }

    private void add(String id, String version, ModDependencyDefinition.Requirement requirement,
                     Action<? super ModDependencyDefinition> action) {
        Objects.requireNonNull(action, "action");
        ModDependencyDefinition dependency = new ModDependencyDefinition(id, version, requirement);
        if (dependencies.putIfAbsent(id, dependency) != null) {
            throw new IllegalArgumentException("Duplicate mod dependency " + id);
        }
        action.execute(dependency);
    }
}
