package uk.co.enderfall.sdk.api.client.ui;

/** Source-pixel borders used by nine-sliced sprites. */
public record UiInsets(int left, int top, int right, int bottom) {
    public UiInsets {
        if (left < 0 || top < 0 || right < 0 || bottom < 0
                || left > 16_384 || top > 16_384 || right > 16_384 || bottom > 16_384) {
            throw new IllegalArgumentException("UI insets must be within 0..16384 pixels");
        }
    }

    public static UiInsets uniform(int value) { return new UiInsets(value, value, value, value); }
}
