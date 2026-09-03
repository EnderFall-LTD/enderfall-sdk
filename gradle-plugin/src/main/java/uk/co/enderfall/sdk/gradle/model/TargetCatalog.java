package uk.co.enderfall.sdk.gradle.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Reviewed target matrix. Values are exact and deliberately contain no dynamic ranges. */
public final class TargetCatalog {
    public static final String SDK_VERSION = "0.1.0-beta.1";
    private static final Map<String, TargetDefinition> TARGETS = createTargets();

    private TargetCatalog() {
    }

    public static Optional<TargetDefinition> find(String minecraftVersion, String loader) {
        return Optional.ofNullable(TARGETS.get(minecraftVersion + '-' + loader));
    }

    public static Collection<TargetDefinition> all() {
        return TARGETS.values();
    }

    public static String supportedIds() {
        return String.join(", ", TARGETS.keySet());
    }

    private static Map<String, TargetDefinition> createTargets() {
        Map<String, TargetDefinition> targets = new LinkedHashMap<>();
        add(targets, new TargetDefinition("1.20.1", "fabric", 17, "0.19.5", "0.92.12+1.20.1"));
        add(targets, new TargetDefinition("1.20.1", "forge", 17, "47.4.23", ""));
        add(targets, new TargetDefinition("1.20.1", "neoforge", 17, "47.1.106", ""));
        add(targets, new TargetDefinition("1.21.1", "fabric", 21, "0.19.5", "0.116.17+1.21.1"));
        add(targets, new TargetDefinition("1.21.1", "neoforge", 21, "21.1.249", ""));
        add(targets, new TargetDefinition("1.21.4", "fabric", 21, "0.19.5", "0.119.4+1.21.4"));
        add(targets, new TargetDefinition("1.21.4", "neoforge", 21, "21.4.157", ""));
        add(targets, new TargetDefinition("26.2", "fabric", 25, "0.19.5", "0.159.0+26.2"));
        add(targets, new TargetDefinition("26.2", "neoforge", 25, "26.2.0.75", ""));
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(targets));
    }

    private static void add(Map<String, TargetDefinition> targets, TargetDefinition target) {
        targets.put(target.id(), target);
    }
}
