package uk.co.enderfall.sdk.api.client.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Loader-neutral focus and hit-testing state for an authored portable element tree.
 *
 * <p>Call {@link #updateTargets(List)} whenever layout changes. List order is keyboard
 * traversal order and later entries are treated as visually above earlier entries for
 * pointer hit-testing.</p>
 */
public final class UiFocusManager {
    public static final int PRIMARY_MOUSE_BUTTON = 0;

    private List<UiFocusTarget> targets = List.of();
    private Map<String, UiFocusTarget> targetsById = Map.of();
    private String focusedId;

    public void updateTargets(List<UiFocusTarget> updatedTargets) {
        Objects.requireNonNull(updatedTargets, "updatedTargets");
        List<UiFocusTarget> copied = List.copyOf(updatedTargets);
        Map<String, UiFocusTarget> indexed = new HashMap<>();
        for (UiFocusTarget target : copied) {
            Objects.requireNonNull(target, "updatedTargets contains null");
            if (indexed.put(target.id(), target) != null) {
                throw new IllegalArgumentException("Duplicate UI focus target ID: " + target.id());
            }
        }
        targets = copied;
        targetsById = Map.copyOf(indexed);
        if (focusedId != null) {
            UiFocusTarget focused = targetsById.get(focusedId);
            if (focused == null || !focused.enabled()) focusedId = null;
        }
    }

    public List<UiFocusTarget> targets() {
        return targets;
    }

    public Optional<String> focusedId() {
        return Optional.ofNullable(focusedId);
    }

    public boolean isFocused(String id) {
        return Objects.requireNonNull(id, "id").equals(focusedId);
    }

    public boolean requestFocus(String id) {
        UiFocusTarget target = targetsById.get(Objects.requireNonNull(id, "id"));
        if (target == null || !target.enabled()) return false;
        focusedId = id;
        return true;
    }

    public void clearFocus() {
        focusedId = null;
    }

    public Optional<String> targetAt(double x, double y) {
        for (int index = targets.size() - 1; index >= 0; index--) {
            UiFocusTarget target = targets.get(index);
            if (target.enabled() && target.bounds().contains(x, y)) return Optional.of(target.id());
        }
        return Optional.empty();
    }

    /** Focuses the topmost target under a primary click, clearing focus outside all targets. */
    public boolean mouseClicked(double x, double y, int button) {
        if (button != PRIMARY_MOUSE_BUTTON) return false;
        Optional<String> hit = targetAt(x, y);
        focusedId = hit.orElse(null);
        return hit.isPresent();
    }

    /** Moves focus in declared target order, wrapping at either end. */
    public boolean moveFocus(boolean backwards) {
        List<UiFocusTarget> enabledTargets = new ArrayList<>();
        for (UiFocusTarget target : targets) {
            if (target.enabled()) enabledTargets.add(target);
        }
        if (enabledTargets.isEmpty()) {
            focusedId = null;
            return false;
        }
        int current = -1;
        for (int index = 0; index < enabledTargets.size(); index++) {
            if (enabledTargets.get(index).id().equals(focusedId)) {
                current = index;
                break;
            }
        }
        int next;
        if (current < 0) {
            next = backwards ? enabledTargets.size() - 1 : 0;
        } else {
            int direction = backwards ? -1 : 1;
            next = Math.floorMod(current + direction, enabledTargets.size());
        }
        focusedId = enabledTargets.get(next).id();
        return true;
    }

    /** Handles portable focus-navigation keys. Other keys remain available to the caller. */
    public boolean keyPressed(int key, int modifiers) {
        if (key != UiKeys.TAB) return false;
        return moveFocus(UiModifiers.contains(modifiers, UiModifiers.SHIFT));
    }

    public UiWidgetState state(String id, double pointerX, double pointerY) {
        UiFocusTarget target = targetsById.get(Objects.requireNonNull(id, "id"));
        if (target == null) throw new IllegalArgumentException("Unknown UI focus target ID: " + id);
        return new UiWidgetState(target.enabled(), target.enabled() && target.bounds().contains(pointerX, pointerY),
                id.equals(focusedId));
    }

    public void reset() {
        targets = List.of();
        targetsById = Map.of();
        focusedId = null;
    }
}
