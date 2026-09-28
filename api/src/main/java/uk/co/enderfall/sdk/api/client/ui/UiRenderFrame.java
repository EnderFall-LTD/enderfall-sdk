package uk.co.enderfall.sdk.api.client.ui;

/** Immutable viewport and pointer values for one rendered frame. */
public record UiRenderFrame(int width, int height, double mouseX, double mouseY,
                            float partialTick, long frameNanos) {
    public UiRenderFrame {
        if (width <= 0 || height <= 0 || width > 1_048_576 || height > 1_048_576) {
            throw new IllegalArgumentException("Invalid UI viewport dimensions");
        }
        if (!Double.isFinite(mouseX) || !Double.isFinite(mouseY)
                || !Float.isFinite(partialTick) || partialTick < 0 || partialTick > 1
                || frameNanos < 0) {
            throw new IllegalArgumentException("Invalid UI frame values");
        }
    }
}
