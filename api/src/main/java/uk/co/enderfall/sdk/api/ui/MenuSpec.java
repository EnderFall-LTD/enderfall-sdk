package uk.co.enderfall.sdk.api.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Declarative layout for a loader-neutral synchronized screen. */
public final class MenuSpec {
    private final String title;
    private final int width;
    private final int height;
    private final int backgroundColor;
    private final List<MenuLabel> labels;
    private final List<MenuButton> buttons;
    private final List<MenuGauge> gauges;
    private final List<MenuTextInput> textInputs;
    private final List<MenuSelectionList> selectionLists;

    private MenuSpec(Builder builder) {
        title = builder.title;
        width = builder.width;
        height = builder.height;
        backgroundColor = builder.backgroundColor;
        labels = Collections.unmodifiableList(new ArrayList<>(builder.labels));
        buttons = Collections.unmodifiableList(new ArrayList<>(builder.buttons));
        gauges = List.copyOf(builder.gauges);
        textInputs = List.copyOf(builder.textInputs);
        selectionLists = List.copyOf(builder.selectionLists);
    }

    public static Builder builder(String title) {
        return new Builder(title);
    }

    public String title() {
        return title;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int backgroundColor() {
        return backgroundColor;
    }

    public List<MenuLabel> labels() {
        return labels;
    }

    public List<MenuButton> buttons() {
        return buttons;
    }

    public List<MenuGauge> gauges() { return gauges; }

    public List<MenuTextInput> textInputs() { return textInputs; }

    public List<MenuSelectionList> selectionLists() { return selectionLists; }

    /** Resolves visible server-owned rows for the native renderer. */
    public List<MenuSelectionVisual> selectionVisuals(MenuState state) {
        Objects.requireNonNull(state, "state");
        List<MenuSelectionVisual> result = new ArrayList<>();
        for (MenuSelectionList list : selectionLists) result.addAll(list.visuals(state));
        return List.copyOf(result);
    }

    public java.util.Optional<MenuTextInput> textInput(String key) {
        Objects.requireNonNull(key, "key");
        return textInputs.stream().filter(input -> input.key().equals(key)).findFirst();
    }

    public boolean supportsAction(String action) {
        return buttons.stream().anyMatch(button -> button.action().equals(action));
    }

    /** Whether the active state permits this declared action right now. */
    public boolean actionEnabled(String action, MenuState state) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(state, "state");
        for (MenuSelectionList list : selectionLists) {
            if (list.owns(action)) return list.enabled(action, state);
        }
        return supportsAction(action);
    }

    /** Resolves state templates and marks the currently selected list row. */
    public String buttonText(MenuButton button, MenuState state) {
        Objects.requireNonNull(button, "button");
        Objects.requireNonNull(state, "state");
        String resolved = state.resolve(button.text());
        for (MenuSelectionList list : selectionLists) {
            if (list.owns(button.action())) return list.displayText(button.action(), resolved, state);
        }
        return resolved;
    }

    /** Returns a permitted page action when the pointer scrolls over a selection list. */
    public java.util.Optional<String> scrollAction(double localX, double localY,
            double verticalAmount, MenuState state) {
        Objects.requireNonNull(state, "state");
        for (MenuSelectionList list : selectionLists) {
            java.util.Optional<String> action = list.scrollAction(localX, localY, verticalAmount, state);
            if (action.isPresent()) return action;
        }
        return java.util.Optional.empty();
    }

    public static final class Builder {
        private final String title;
        private int width = 220;
        private int height = 140;
        private int backgroundColor = 0xEE15111F;
        private final List<MenuLabel> labels = new ArrayList<>();
        private final List<MenuButton> buttons = new ArrayList<>();
        private final Set<String> actions = new LinkedHashSet<>();
        private final List<MenuGauge> gauges = new ArrayList<>();
        private final List<MenuTextInput> textInputs = new ArrayList<>();
        private final Set<String> inputKeys = new LinkedHashSet<>();
        private final List<MenuSelectionList> selectionLists = new ArrayList<>();
        private final Set<String> selectionKeys = new LinkedHashSet<>();

        public Builder selectionList(MenuSelectionList list) {
            Objects.requireNonNull(list, "list");
            if (selectionLists.size() >= 2) {
                throw new IllegalArgumentException("A menu supports at most two selection lists");
            }
            if (!selectionKeys.add(list.key())) {
                throw new IllegalArgumentException("Duplicate menu selection-list key: " + list.key());
            }
            for (MenuButton button : list.buttons()) addButton(button);
            selectionLists.add(list);
            return this;
        }

        public Builder gauge(MenuGauge gauge) {
            Objects.requireNonNull(gauge, "gauge");
            if (gauges.size() >= 8) throw new IllegalArgumentException("A menu supports at most eight gauges");
            gauges.add(gauge);
            return this;
        }

        public Builder textInput(MenuTextInput input) {
            Objects.requireNonNull(input, "input");
            if (textInputs.size() >= 8) {
                throw new IllegalArgumentException("A menu supports at most eight text inputs");
            }
            if (!inputKeys.add(input.key())) {
                throw new IllegalArgumentException("Duplicate menu text-input key: " + input.key());
            }
            textInputs.add(input);
            return this;
        }

        private Builder(String title) {
            this.title = Objects.requireNonNull(title, "title");
            if (title.isBlank() || title.length() > 256) {
                throw new IllegalArgumentException("Menu title must contain 1-256 characters");
            }
        }

        public Builder size(int width, int height) {
            if (width < 120 || width > 320 || height < 80 || height > 240) {
                throw new IllegalArgumentException("Menu size must be between 120x80 and 320x240");
            }
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder backgroundColor(int argb) {
            backgroundColor = argb;
            return this;
        }

        public Builder label(MenuLabel label) {
            if (labels.size() >= 24) {
                throw new IllegalArgumentException("A menu supports at most 24 labels");
            }
            labels.add(Objects.requireNonNull(label, "label"));
            return this;
        }

        public Builder button(MenuButton button) {
            Objects.requireNonNull(button, "button");
            return addButton(button);
        }

        private Builder addButton(MenuButton button) {
            if (buttons.size() >= 24) throw new IllegalArgumentException("A menu supports at most 24 buttons");
            if (!actions.add(button.action())) {
                throw new IllegalArgumentException("Duplicate menu action: " + button.action());
            }
            buttons.add(button);
            return this;
        }

        public MenuSpec build() {
            for (MenuGauge gauge : gauges) {
                if (gauge.x() + gauge.width() > width || gauge.y() + gauge.height() > height) {
                    throw new IllegalArgumentException("Menu gauge is outside the panel");
                }
            }
            for (MenuLabel label : labels) {
                if (label.x() < 0 || label.x() > width || label.y() < 0 || label.y() > height) {
                    throw new IllegalArgumentException("Menu label is outside the panel: " + label.text());
                }
            }
            for (MenuButton button : buttons) {
                if (button.x() < 0 || button.y() < 0
                        || button.x() + button.width() > width
                        || button.y() + button.height() > height) {
                    throw new IllegalArgumentException("Menu button is outside the panel: " + button.action());
                }
            }
            for (MenuTextInput input : textInputs) {
                if (input.x() < 0 || input.y() < 0
                        || input.x() + input.width() > width
                        || input.y() + input.height() > height) {
                    throw new IllegalArgumentException("Menu text input is outside the panel: " + input.key());
                }
            }
            for (MenuSelectionList list : selectionLists) {
                if (list.x() + list.width() > width || list.y() + list.height() > height) {
                    throw new IllegalArgumentException("Menu selection list is outside the panel: " + list.key());
                }
            }
            return new MenuSpec(this);
        }
    }
}
