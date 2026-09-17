package uk.co.enderfall.sdk.runtime.blockentity;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

/** Pure server processing decisions. Native code must commit a completion before accepting its next state. */
public final class PortableProcessingCycle {
    private PortableProcessingCycle() { }

    /** Revision identifies the effective recipe definition, not merely its resource ID. */
    public record Job(ResourceId recipe, String revision, int durationTicks) {
        public Job {
            Objects.requireNonNull(recipe, "recipe");
            Objects.requireNonNull(revision, "revision");
            if (revision.isBlank() || revision.length() > 256) throw new IllegalArgumentException("Invalid recipe revision");
            if (durationTicks < 1 || durationTicks > 1_728_000) throw new IllegalArgumentException("Duration must be 1..1728000 ticks");
        }
    }

    public record State(Job job, int elapsedTicks) {
        public State {
            if (elapsedTicks < 0 || (job == null ? elapsedTicks != 0 : elapsedTicks >= job.durationTicks())) {
                throw new IllegalArgumentException("Invalid processing progress");
            }
        }
        public static State idle() { return new State(null, 0); }
    }

    public enum Status { IDLE, PROCESSING, OUTPUT_BLOCKED, COMPLETE }
    public record Step(State next, Status status) { }

    /**
     * A missing matching job resets progress. A changed recipe/revision/duration restarts
     * it. A full output pauses progress without consuming inputs, even on the final tick.
     * No wall-clock catch-up occurs for unloaded blocks. Inputs are consumed only when
     * the caller successfully commits COMPLETE together with the stored output and next state.
     */
    public static Step advance(State previous, Job matchingJob, boolean outputFits) {
        Objects.requireNonNull(previous, "previous");
        if (matchingJob == null) return new Step(State.idle(), Status.IDLE);
        int elapsed = matchingJob.equals(previous.job()) ? previous.elapsedTicks() : 0;
        if (!outputFits) return new Step(new State(matchingJob, elapsed), Status.OUTPUT_BLOCKED);
        if (elapsed + 1 == matchingJob.durationTicks()) return new Step(State.idle(), Status.COMPLETE);
        return new Step(new State(matchingJob, elapsed + 1), Status.PROCESSING);
    }
}
