package uk.co.enderfall.sdk.bridge;

/** Checked failure raised before or during bridge output generation. */
public final class BridgeGenerationException extends Exception {
    private static final long serialVersionUID = 1L;

    /** Creates a generation failure with a user-actionable message. */
    public BridgeGenerationException(String message) {
        super(message);
    }

    /** Creates a generation failure retaining the underlying I/O cause. */
    public BridgeGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
