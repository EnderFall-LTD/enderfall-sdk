package uk.co.enderfall.sdk.runtime.blockentity;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NamedAnimationControllerTest {
    @Test void onlyCommittedChangesNotifyAndSnapshotsStayImmutable() {
        var changes = new AtomicInteger();
        var controller = new NamedAnimationController(Set.of("open", "close"), changes::incrementAndGet);
        controller.stop(0);
        controller.play("open", 10);
        var first = controller.snapshot().orElseThrow();
        controller.play("open", 10);
        assertEquals(1, changes.get());
        controller.stop(15);
        controller.stop(20);
        assertEquals(2, changes.get());
        assertNull(first.stopTick());
        assertThrows(IllegalArgumentException.class, () -> controller.play("missing", 20));
        assertEquals(2, changes.get());
        controller.play("close", 20);
        assertEquals("close", controller.snapshot().orElseThrow().animation());
        assertEquals(3, changes.get());
    }
}
