package uk.co.enderfall.sdk.api.client.ui;

import java.util.Objects;

/** Native lifecycle policy for a portable client-only screen. */
public record ClientScreenSpec(String title, boolean pausesGame, boolean closeOnEscape) {
    public ClientScreenSpec {
        Objects.requireNonNull(title, "title");
        if (title.isBlank() || title.length() > 256 || title.indexOf('\n') >= 0 || title.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Client screen title must be one line with 1-256 characters");
        }
    }

    public static ClientScreenSpec of(String title) { return new ClientScreenSpec(title, false, true); }
}
