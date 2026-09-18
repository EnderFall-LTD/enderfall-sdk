package uk.co.enderfall.sdk.api.block;

/** Selects how a portable six-way block chooses its initial native facing. */
public enum SixWayPlacement {
    /** Face out from the surface that the player clicked. Reliable for attached furniture and fixtures. */
    CLICKED_FACE,
    /** Face opposite the player's nearest look direction, matching dispenser-like placement. */
    VIEW_DIRECTION
}
