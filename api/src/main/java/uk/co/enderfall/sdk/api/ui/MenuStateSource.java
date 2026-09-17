package uk.co.enderfall.sdk.api.ui;

import java.util.Optional;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/** Read-only server-thread snapshot provider. Never load chunks to obtain a snapshot. */
@Experimental("Live menu state binding")
@FunctionalInterface
public interface MenuStateSource {
    /**
     * Returns current state, or empty when the backing object or player access is no longer valid.
     * Empty closes the session. Implementations must not open, update, or close menus here.
     */
    Optional<MenuState> snapshot();
}
