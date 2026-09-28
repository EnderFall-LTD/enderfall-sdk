package uk.co.enderfall.sdk.gradle.model;

import java.util.Locale;
import java.util.Objects;

/** One portable API plus target-runtime mod dependency declared by a consumer. */
public final class ModDependencyDefinition {
    public enum Requirement { REQUIRED, OPTIONAL }
    public enum Side { BOTH, CLIENT, SERVER }
    public enum Ordering { NONE, BEFORE, AFTER }

    private final String id;
    private final String version;
    private final Requirement requirement;
    private String api = "";
    private String target = "";
    private Side side = Side.BOTH;
    private Ordering ordering = Ordering.NONE;
    private boolean exactVersion;
    private boolean developmentRuntime;

    ModDependencyDefinition(String id, String version, Requirement requirement) {
        this.id = Objects.requireNonNull(id, "id");
        this.version = Objects.requireNonNull(version, "version");
        this.requirement = Objects.requireNonNull(requirement, "requirement");
        this.developmentRuntime = requirement == Requirement.REQUIRED;
    }

    public String getId() { return id; }

    public String getVersion() { return version; }

    public Requirement getRequirement() { return requirement; }

    public String getApi() { return api; }

    public void setApi(String api) { this.api = Objects.requireNonNull(api, "api"); }

    public String getTarget() { return target; }

    public void setTarget(String target) { this.target = Objects.requireNonNull(target, "target"); }

    public Side getSide() { return side; }

    public void setSide(Side side) { this.side = Objects.requireNonNull(side, "side"); }

    public void setSide(String side) {
        this.side = Side.valueOf(Objects.requireNonNull(side, "side").toUpperCase(Locale.ROOT));
    }

    public Ordering getOrdering() { return ordering; }

    public void setOrdering(Ordering ordering) {
        this.ordering = Objects.requireNonNull(ordering, "ordering");
    }

    public void setOrdering(String ordering) {
        this.ordering = Ordering.valueOf(Objects.requireNonNull(ordering, "ordering").toUpperCase(Locale.ROOT));
    }

    public boolean isExactVersion() { return exactVersion; }

    public void setExactVersion(boolean exactVersion) { this.exactVersion = exactVersion; }

    public boolean isDevelopmentRuntime() { return developmentRuntime; }

    public void setDevelopmentRuntime(boolean developmentRuntime) {
        this.developmentRuntime = developmentRuntime;
    }

    public String resolveTarget(TargetDefinition targetDefinition) {
        Objects.requireNonNull(targetDefinition, "targetDefinition");
        return target.replace("{minecraft}", targetDefinition.minecraftVersion())
                .replace("{loader}", targetDefinition.loader());
    }

    public String metadataDescriptor() {
        return String.join("|", id, version, requirement.name(), side.name(), ordering.name(),
                Boolean.toString(exactVersion));
    }
}
