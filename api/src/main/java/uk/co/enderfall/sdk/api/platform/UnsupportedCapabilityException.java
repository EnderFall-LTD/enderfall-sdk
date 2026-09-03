package uk.co.enderfall.sdk.api.platform;

/** Raised during initialization when a required target capability is unavailable. */
public final class UnsupportedCapabilityException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public UnsupportedCapabilityException(Capability capability, String target) {
        super("Required capability " + capability + " is unavailable on " + target);
    }
}
