package uk.co.enderfall.sdk.api.client.ui;

/** Stable modifier masks delivered to portable keyboard callbacks. */
public final class UiModifiers {
    public static final int SHIFT = 0x0001;
    public static final int CONTROL = 0x0002;
    public static final int ALT = 0x0004;
    public static final int SUPER = 0x0008;
    public static final int CAPS_LOCK = 0x0010;
    public static final int NUM_LOCK = 0x0020;

    private UiModifiers() {
    }

    public static boolean contains(int modifiers, int required) {
        return (modifiers & required) == required;
    }
}
