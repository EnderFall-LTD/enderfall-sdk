package uk.co.enderfall.sdk.api.fluid;

import uk.co.enderfall.sdk.api.annotation.Experimental;

/** Operations exposed by a fluid connection. Internal machine access may use a different view. */
@Experimental
public enum FluidPortMode {
    CLOSED(false, false), INPUT(true, false), OUTPUT(false, true), BOTH(true, true);

    private final boolean fill;
    private final boolean drain;
    FluidPortMode(boolean fill, boolean drain) { this.fill = fill; this.drain = drain; }
    public boolean allowsFill() { return fill; }
    public boolean allowsDrain() { return drain; }
}
