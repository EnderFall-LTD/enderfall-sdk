package uk.co.enderfall.sdk.api.client.ui;

/** Native-font measurement returned in GUI pixels. */
public record UiTextMetrics(int width, int lineHeight) {
    public UiTextMetrics {
        if (width < 0 || width > 1_048_576 || lineHeight <= 0 || lineHeight > 4096) {
            throw new IllegalArgumentException("Invalid UI text metrics");
        }
    }
}
