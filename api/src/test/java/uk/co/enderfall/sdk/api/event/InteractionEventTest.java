package uk.co.enderfall.sdk.api.event;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.item.ItemDataKey;
import uk.co.enderfall.sdk.api.item.MutableItemData;

class InteractionEventTest {
    private static final class TestData implements MutableItemData {
        private final java.util.Map<ItemDataKey<?>, Object> values = new java.util.HashMap<>();
        @Override public <T> java.util.Optional<T> get(ItemDataKey<T> key) {
            return java.util.Optional.ofNullable(values.get(key)).map(key::validate);
        }
        @Override public <T> void set(ItemDataKey<T> key, T value) { values.put(key, key.validate(value)); }
        @Override public void remove(ItemDataKey<?> key) { values.remove(key); }
    }

    private static final class TestStack implements uk.co.enderfall.sdk.api.item.MutableItemStack {
        private int count = 1;
        private int damage;
        @Override public int count() { return count; }
        @Override public int maxStackSize() { return 1; }
        @Override public boolean damageable() { return true; }
        @Override public int damage() { return damage; }
        @Override public int maxDamage() { return 8; }
        @Override public boolean damage(int amount) {
            damage += amount;
            if (damage >= maxDamage()) { count = 0; return true; }
            return false;
        }
        @Override public int repair(int amount) {
            int repaired = Math.min(amount, damage);
            damage -= repaired;
            return repaired;
        }
        @Override public int consume(int amount) {
            int consumed = Math.min(amount, count);
            count -= consumed;
            return consumed;
        }
    }

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

    @Test
    void completeContextCanExposeTheActualHeldStackData() {
        ResourceId item = ResourceId.parse("example:letter");
        ItemDataKey<String> text = ItemDataKey.string(ResourceId.parse("example:letter_text"), 64);
        TestData data = new TestData();
        InteractionEvent event = new InteractionEvent(InteractionEvent.Kind.USE_ITEM,
                InteractionEvent.Side.SERVER, UUID.randomUUID(), item, null, item,
                InteractionEvent.Hand.MAIN_HAND, false, data);

        event.itemData().orElseThrow().set(text, "hello");
        assertEquals("hello", data.get(text).orElseThrow());
    }

    @Test
    void completeContextExposesCreativeModeAndMutableHeldStack() {
        ResourceId item = ResourceId.parse("example:wrench");
        TestStack stack = new TestStack();
        InteractionEvent event = new InteractionEvent(InteractionEvent.Kind.USE_ITEM,
                InteractionEvent.Side.SERVER, UUID.randomUUID(), item, null, item,
                InteractionEvent.Hand.MAIN_HAND, false, true, null, stack);

        assertTrue(event.creativeMode());
        assertEquals(8, event.itemStack().orElseThrow().remainingDurability());
        assertFalse(event.itemStack().orElseThrow().damage(3));
        assertEquals(5, event.itemStack().orElseThrow().remainingDurability());
        assertEquals(3, event.itemStack().orElseThrow().repair(20));
        assertEquals(8, event.itemStack().orElseThrow().remainingDurability());
    }
}
