package uk.co.enderfall.sdk.runtime.blockentity;

import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import static org.junit.jupiter.api.Assertions.*;
import static uk.co.enderfall.sdk.runtime.blockentity.PortableProcessingCycle.*;

class PortableProcessingCycleTest {
    private final Job job = new Job(ResourceId.parse("test:assembly"), "recipe-v1", 3);

    @Test void completesOnExactTickAndStartsNextBatchFromZero() {
        var first = advance(State.idle(), job, true);
        var second = advance(first.next(), job, true);
        assertEquals(2, second.next().elapsedTicks());
        var third = advance(second.next(), job, true);
        assertEquals(Status.COMPLETE, third.status());
        assertEquals(State.idle(), third.next());
        assertEquals(1, advance(third.next(), job, true).next().elapsedTicks());
    }

    @Test void fullOutputPausesEvenAtCompletionBoundary() {
        var almostDone = new State(job, 2);
        var blocked = advance(almostDone, job, false);
        assertEquals(Status.OUTPUT_BLOCKED, blocked.status());
        assertEquals(almostDone, blocked.next());
        assertEquals(Status.COMPLETE, advance(blocked.next(), job, true).status());
    }

    @Test void removedInputsResetAndChangedRecipeCannotReuseProgress() {
        var previous = new State(job, 2);
        assertEquals(State.idle(), advance(previous, null, true).next());
        var changed = new Job(job.recipe(), "recipe-v2", 3);
        assertEquals(1, advance(previous, changed, true).next().elapsedTicks());
        assertEquals(0, advance(previous, changed, false).next().elapsedTicks());
    }

    @Test void validatesRestoredStateAndDuration() {
        assertThrows(IllegalArgumentException.class, () -> new State(null, 1));
        assertThrows(IllegalArgumentException.class, () -> new State(job, 3));
        assertThrows(IllegalArgumentException.class, () -> new Job(job.recipe(), "v1", 0));
        assertEquals(Status.COMPLETE, advance(State.idle(), new Job(job.recipe(), "v1", 1), true).status());
    }
}
