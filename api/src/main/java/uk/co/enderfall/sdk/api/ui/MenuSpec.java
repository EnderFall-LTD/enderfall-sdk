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

    private MenuSpec(Builder builder) {
        title = builder.title;
        width = builder.width;
        height = builder.height;
        backgroundColor = builder.backgroundColor;
        labels = Collections.unmodifiableList(new ArrayList<>(builder.labels));
        buttons = Collections.unmodifiableList(new ArrayList<>(builder.buttons));
        gauges = List.copyOf(builder.gauges);
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

    public boolean supportsAction(String action) {
        return buttons.stream().anyMatch(button -> button.action().equals(action));
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

        public Builder gauge(MenuGauge gauge) {
            Objects.requireNonNull(gauge, "gauge");
            if (gauges.size() >= 8) throw new IllegalArgumentException("A menu supports at most eight gauges");
            gauges.add(gauge);
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
            if (buttons.size() >= 12) {
                throw new IllegalArgumentException("A menu supports at most 12 buttons");
            }
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
            return new MenuSpec(this);
        }
    }
}
