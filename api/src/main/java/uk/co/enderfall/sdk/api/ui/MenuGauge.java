package uk.co.enderfall.sdk.api.ui;

import java.util.Objects;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/** Bottom-up, state-driven gauge. The state key must contain an integer percentage. */
@Experimental("Available only in gauge-enabled generated runtimes")
public record MenuGauge(String stateKey, int x, int y, int width, int height, int color) {
    public MenuGauge {
        Objects.requireNonNull(stateKey, "stateKey");
        if (!stateKey.matches("[a-z][a-z0-9_.-]{0,63}")) throw new IllegalArgumentException("Invalid gauge state key");
        if (x < 0 || y < 0 || x > 320 || y > 240 || width < 4 || width > 320 || height < 4 || height > 240) {
            throw new IllegalArgumentException("Invalid gauge bounds");
        }
    }

    /** Missing/malformed state is empty; numeric percentages are clamped. */
    public int filledPixels(MenuState state) {
        Objects.requireNonNull(state, "state");
        try {
            int percent = Integer.parseInt(state.value(stateKey));
            return (height - 2) * Math.max(0, Math.min(100, percent)) / 100;
        } catch (NumberFormatException ignored) { return 0; }
    }
}
