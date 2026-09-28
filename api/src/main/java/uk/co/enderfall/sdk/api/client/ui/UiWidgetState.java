package uk.co.enderfall.sdk.api.client.ui;

/** Render-time interaction state for one registered focus target. */
public record UiWidgetState(boolean enabled, boolean hovered, boolean focused) {
}
