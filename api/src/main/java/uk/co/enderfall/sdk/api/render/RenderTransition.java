package uk.co.enderfall.sdk.api.render;

import java.util.Objects;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/** Stateless pose transition. Callers supply elapsed ticks, including partial ticks.
 * No wall clock, world access, mutable playback state, or network traffic is used.
 * Sampling clamps to the endpoints; looping and server state synchronization are
 * deliberately left to the caller.
 */
@Experimental("Portable render animation")
public record RenderTransition(RenderTransform from, RenderTransform to,
                               double durationTicks, Easing easing) {
    public enum Easing { LINEAR, SMOOTHSTEP }

    public RenderTransition {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(easing, "easing");
        if (!Double.isFinite(durationTicks) || durationTicks <= 0) {
            throw new IllegalArgumentException("Transition duration must be finite and positive");
        }
    }

    public RenderTransform sample(double elapsedTicks) {
        if (!Double.isFinite(elapsedTicks)) {
            throw new IllegalArgumentException("Elapsed ticks must be finite");
        }
        if (elapsedTicks <= 0) return from;
        if (elapsedTicks >= durationTicks) return to;
        double progress = elapsedTicks / durationTicks;
        if (easing == Easing.SMOOTHSTEP) progress = progress * progress * (3 - 2 * progress);
        return from.interpolate(to, (float) progress);
    }
}
