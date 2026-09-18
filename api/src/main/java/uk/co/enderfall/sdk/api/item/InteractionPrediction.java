package uk.co.enderfall.sdk.api.item;

import uk.co.enderfall.sdk.api.annotation.Experimental;

/**
 * Client-side prediction for a portable item interaction.
 *
 * <p>Prediction never runs the authoritative item callback or changes game state. It
 * only tells Minecraft whether the client should send a vanilla fallback action while
 * the server processes the portable callback.</p>
 */
@Experimental("Portable item interaction prediction")
public enum InteractionPrediction {
    /** Preserve normal block, item and other-mod fallback behaviour. */
    PASS,
    /** Predict success and suppress the follow-up vanilla fallback action. */
    HANDLE,
    /** Predict failure and suppress the interaction. */
    CANCEL
}
