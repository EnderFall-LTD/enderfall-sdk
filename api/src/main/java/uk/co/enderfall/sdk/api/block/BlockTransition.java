package uk.co.enderfall.sdk.api.block;

import java.util.Objects;
import java.util.OptionalInt;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/**
 * Immutable result of a portable block callback. It replaces the block's declared
 * portable state and can request one follow-up server tick for the same block.
 */
@Experimental("Portable scheduled block ticks")
public record BlockTransition(PortableBlockState state, OptionalInt scheduleAfterTicks) {
    public BlockTransition {
        Objects.requireNonNull(state, "state");
        scheduleAfterTicks = Objects.requireNonNull(scheduleAfterTicks, "scheduleAfterTicks");
        if (scheduleAfterTicks.isPresent() && scheduleAfterTicks.getAsInt() < 1) {
            throw new IllegalArgumentException("Scheduled block tick delay must be at least one tick");
        }
    }

    /** Applies a state without requesting another scheduled tick. */
    public static BlockTransition stable(PortableBlockState state) {
        return new BlockTransition(state, OptionalInt.empty());
    }

    /** Applies a state and requests another callback after the supplied server ticks. */
    public static BlockTransition after(PortableBlockState state, int delayTicks) {
        return new BlockTransition(state, OptionalInt.of(delayTicks));
    }
}
