package uk.co.enderfall.sdk.api.client.ui;

/** Pixel-space rectangle. Width and height may be zero for an empty intersection. */
public record UiRect(int x, int y, int width, int height) {
    private static final int LIMIT = 1_048_576;

    public UiRect {
        if (Math.abs((long) x) > LIMIT || Math.abs((long) y) > LIMIT
                || width < 0 || height < 0 || width > LIMIT || height > LIMIT) {
            throw new IllegalArgumentException("UI rectangle is outside the supported pixel range");
        }
    }

    public int right() { return Math.addExact(x, width); }

    public int bottom() { return Math.addExact(y, height); }

    public boolean contains(double pointX, double pointY) {
        return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
    }

    public UiRect intersect(UiRect other) {
        java.util.Objects.requireNonNull(other, "other");
        int left = Math.max(x, other.x);
        int top = Math.max(y, other.y);
        int right = Math.min(right(), other.right());
        int bottom = Math.min(bottom(), other.bottom());
        return new UiRect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }
}
