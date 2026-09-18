package uk.co.enderfall.sdk.api.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;

class InteractionEventTest {
    @Test void completeBlockContextExposesHeldItemHandAndSneaking() {
        ResourceId block = ResourceId.of("minecraft", "barrel");
        ResourceId item = ResourceId.of("test", "wrench");
        var location = new uk.co.enderfall.sdk.api.blockentity.BlockLocation(
                ResourceId.of("minecraft", "overworld"), 4, 5, 6);
        var event = new InteractionEvent(InteractionEvent.Kind.USE_BLOCK,
                InteractionEvent.Side.SERVER, java.util.UUID.randomUUID(), block,
                location, item, InteractionEvent.Hand.OFF_HAND, true);
        assertEquals(item, event.heldItem().orElseThrow());
        assertEquals(InteractionEvent.Hand.OFF_HAND, event.hand().orElseThrow());
        assertEquals(location, event.blockLocation().orElseThrow());
        assertTrue(event.sneaking());
    }

    @Test
    void blockEventsRetainExactDimensionAndCoordinates() {
        var location = new uk.co.enderfall.sdk.api.blockentity.BlockLocation(
                ResourceId.of("minecraft", "the_nether"), -31, -12, 74);
        var event = new InteractionEvent(InteractionEvent.Kind.USE_BLOCK, InteractionEvent.Side.SERVER,
                UUID.randomUUID(), ResourceId.of("test", "workbench"), location);
        assertEquals(location, event.blockLocation().orElseThrow());
        event.handle();
        assertEquals(location, event.blockLocation().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> new InteractionEvent(InteractionEvent.Kind.USE_ITEM,
                InteractionEvent.Side.SERVER, UUID.randomUUID(), ResourceId.of("test", "item"), location));
    }

    @Test
    void oldConstructorsDoNotInventBlockLocations() {
        var id = ResourceId.of("test", "block");
        assertTrue(new InteractionEvent(InteractionEvent.Kind.USE_BLOCK, UUID.randomUUID(), id).blockLocation().isEmpty());
        assertTrue(new InteractionEvent(InteractionEvent.Kind.USE_BLOCK, InteractionEvent.Side.CLIENT,
                UUID.randomUUID(), id).blockLocation().isEmpty());
    }
    @Test
    void handledAndCancelledOutcomesAreMutuallyExclusive() {
        InteractionEvent event = new InteractionEvent(InteractionEvent.Kind.USE_ITEM,
                InteractionEvent.Side.SERVER, UUID.randomUUID(), ResourceId.of("test", "item"));

        event.handle();
        assertTrue(event.handled());
        assertFalse(event.cancelled());

        event.cancel();
        assertFalse(event.handled());
        assertTrue(event.cancelled());
    }
}
