package uk.co.enderfall.sdk.runtime;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ui.MenuRef;
import uk.co.enderfall.sdk.api.ui.MenuSpec;
import uk.co.enderfall.sdk.api.ui.MenuState;

/** Immutable bridge value passed from runtime-core to a target's client screen implementation. */
public record PortableMenuView(long sessionId, MenuRef menu, MenuSpec spec, MenuState state) {
    public PortableMenuView {
        if (sessionId <= 0) {
            throw new IllegalArgumentException("sessionId must be positive");
        }
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(state, "state");
    }
}
