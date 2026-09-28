package uk.co.enderfall.sdk.api.ui;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Ordered, server-authoritative shift-click routing between named slot groups. */
public record InventoryQuickMoveRule(String sourceGroup, List<String> targetGroups) {
    public static final String PLAYER_MAIN = "player_main";
    public static final String PLAYER_HOTBAR = "player_hotbar";

    public InventoryQuickMoveRule {
        sourceGroup = requireGroup(sourceGroup);
        Objects.requireNonNull(targetGroups, "targetGroups");
        if (targetGroups.isEmpty() || targetGroups.size() > 16) {
            throw new IllegalArgumentException("A quick-move rule requires 1-16 target groups");
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String target : targetGroups) {
            target = requireGroup(target);
            if (target.equals(sourceGroup)) {
                throw new IllegalArgumentException("A quick-move rule cannot route a group to itself");
            }
            if (!unique.add(target)) throw new IllegalArgumentException("Duplicate quick-move target group: " + target);
        }
        targetGroups = List.copyOf(unique);
    }

    public InventoryQuickMoveRule(String sourceGroup, String... targetGroups) {
        this(sourceGroup, List.of(targetGroups));
    }

    private static String requireGroup(String value) {
        Objects.requireNonNull(value, "group");
        if (!value.matches("[a-z][a-z0-9_]{0,31}")) {
            throw new IllegalArgumentException("Quick-move group must match [a-z][a-z0-9_]{0,31}: " + value);
        }
        return value;
    }
}
