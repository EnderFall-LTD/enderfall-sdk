package uk.co.enderfall.sdk.api.render;

import java.util.Objects;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/** World-game-time driven visual playback. No server triggers or mutable playback state. */
@Experimental("Portable model animation")
public record RenderAnimation(RenderTransition transition, Playback playback, double startTick) {
    public enum Playback {
        /** Alternates forward and backward without an endpoint jump. */
        PING_PONG,
        /** Restarts at the first pose at each duration boundary. */
        LOOP,
        /** Plays once, then holds the final pose. */
        ONCE
    }

    public RenderAnimation(RenderTransition transition) {
        this(transition, Playback.PING_PONG, 0);
    }

    public RenderAnimation(RenderTransition transition, Playback playback) {
        this(transition, playback, 0);
    }

    public RenderAnimation {
        Objects.requireNonNull(transition, "transition");
        Objects.requireNonNull(playback, "playback");
        if (!Double.isFinite(startTick) || startTick < 0) {
            throw new IllegalArgumentException("Start tick must be finite and non-negative");
        }
    }

    /** Returns a plan scheduled at an absolute world game tick, not a delay from registration. */
    public RenderAnimation startingAt(double tick) {
        return new RenderAnimation(transition, playback, tick);
    }

    /** Samples absolute world game time; holds the first pose until the start tick. */
    public RenderTransform sample(double gameTicks) {
        if (!Double.isFinite(gameTicks) || gameTicks < 0) {
            throw new IllegalArgumentException("Game ticks must be finite and non-negative");
        }
        if (gameTicks <= startTick) return transition.from();
        double elapsed = gameTicks - startTick;
        double duration = transition.durationTicks();
        if (playback == Playback.ONCE) return transition.sample(elapsed);
        if (playback == Playback.LOOP) return transition.sample(elapsed % duration);
        // Reduce time before dividing: finite, tiny durations must not overflow
        // the cycle count and crash a render callback. Avoid doubling huge durations.
        double phase = duration <= Double.MAX_VALUE / 2 ? elapsed % (duration * 2) : elapsed;
        return transition.sample(phase <= duration ? phase : duration - (phase - duration));
    }
}
