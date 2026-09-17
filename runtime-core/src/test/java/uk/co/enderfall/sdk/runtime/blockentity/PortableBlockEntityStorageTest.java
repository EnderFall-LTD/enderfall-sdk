package uk.co.enderfall.sdk.runtime.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.blockentity.BlockEntityInt;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;
import uk.co.enderfall.sdk.api.registry.BlockRef;

class PortableBlockEntityStorageTest {
    @Test void machineCommitValidatesEverythingBeforeAnyMutation() {
        var tankSpec = new uk.co.enderfall.sdk.api.fluid.FluidTankSpec("water", 100);
        var spec = BlockEntitySpec.builder(BLOCK).inventorySlots(1).field(PROGRESS).tank(tankSpec).build();
        var owner = new PortableBlockEntityStorage<>(spec, CODEC);
        var tank = owner.tank(tankSpec);
        tank.fill(new uk.co.enderfall.sdk.api.fluid.FluidVolume(ResourceId.parse("minecraft:water"), 80), false);
        byte[] before = owner.save();
        long revision = owner.revision();
        assertThrows(IllegalArgumentException.class, () -> owner.replaceInventoryFieldsAndTanks(
                java.util.List.of(new byte[] {1}), java.util.Map.of(PROGRESS, 999), java.util.Map.of("water", java.util.Optional.empty())));
        assertArrayEquals(before, owner.save());
        assertEquals(revision, owner.revision());
        assertThrows(IllegalArgumentException.class, () -> owner.replaceInventoryFieldsAndTanks(
                java.util.List.of(new byte[PortableBlockEntityStorage.MAXIMUM_STACK_BYTES + 1]), java.util.Map.of(),
                java.util.Map.of("water", java.util.Optional.empty())));
        assertArrayEquals(before, owner.save());
        owner.replaceInventoryFieldsAndTanks(java.util.List.of(new byte[] {1}), java.util.Map.of(PROGRESS, 10),
                java.util.Map.of("water", java.util.Optional.empty()));
        assertTrue(tank.contents().isEmpty());
        assertEquals(10, owner.get(PROGRESS));
        assertArrayEquals(new byte[] {1}, owner.stack(0));
    }
    @Test void transferMarksBothOwnersAndSimulationDoesNot() {
        var tankSpec = new uk.co.enderfall.sdk.api.fluid.FluidTankSpec("water", 100);
        var spec = BlockEntitySpec.builder(BLOCK).tank(tankSpec).build();
        var first = new PortableBlockEntityStorage<>(spec, CODEC);
        var second = new PortableBlockEntityStorage<>(spec, CODEC);
        var water = new uk.co.enderfall.sdk.api.fluid.FluidVolume(ResourceId.parse("minecraft:water"), 80);
        first.tank(tankSpec).fill(water, false);
        first.markPersisted(first.revision());
        assertEquals(50, first.transferTank(tankSpec, second, tankSpec, 50, true));
        assertFalse(first.dirty());
        assertFalse(second.dirty());
        assertEquals(50, first.transferTank(tankSpec, second, tankSpec, 50, false));
        assertTrue(first.dirty());
        assertTrue(second.dirty());
        assertEquals(30, first.tank(tankSpec).contents().orElseThrow().amount());
        assertEquals(50, second.tank(tankSpec).contents().orElseThrow().amount());
    }
    @Test void namedTanksPersistAndCorruptionDoesNotPartiallyLoad() {
        var tankSpec = new uk.co.enderfall.sdk.api.fluid.FluidTankSpec("water", 100);
        var spec = BlockEntitySpec.builder(BLOCK).inventorySlots(4).field(PROGRESS).tank(tankSpec).build();
        var owner = new PortableBlockEntityStorage<>(spec, CODEC);
        var tank = owner.tank(tankSpec);
        var water = new uk.co.enderfall.sdk.api.fluid.FluidVolume(ResourceId.parse("minecraft:water"), 80);
        tank.fill(water, true);
        assertFalse(owner.dirty());
        tank.fill(water, false);
        assertTrue(owner.dirty());
        byte[] snapshot = owner.save();
        owner.markPersisted(owner.revision());
        tank.drain(water.fluid(), 10, false);
        assertTrue(owner.dirty());
        owner.restore(snapshot);
        assertEquals(water, tank.contents().orElseThrow());
        byte[] corrupt = Arrays.copyOf(snapshot, snapshot.length - 1);
        long revision = owner.revision();
        assertThrows(IllegalArgumentException.class, () -> owner.restore(corrupt));
        assertEquals(revision, owner.revision());
        assertArrayEquals(snapshot, owner.save());
        assertThrows(IllegalArgumentException.class, () -> storage().restore(snapshot));
        owner.restore(storage().save());
        assertTrue(tank.contents().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> owner.tank(
                new uk.co.enderfall.sdk.api.fluid.FluidTankSpec("water", 99)));
        assertThrows(IllegalArgumentException.class, () -> BlockEntitySpec.builder(BLOCK).tank(tankSpec).tank(tankSpec));
    }

    @Test void inventoryAndProgressCommitTogetherOrNotAtAll() {
        var storage = storage();
        storage.set(PROGRESS, 199);
        storage.stack(0, new byte[] {5});
        byte[] before = storage.save();
        long revision = storage.revision();
        var completed = java.util.List.of(new byte[0], new byte[0], new byte[0], new byte[] {9});
        assertThrows(IllegalArgumentException.class, () -> storage.replaceInventoryAndFields(
                completed, java.util.Map.of(PROGRESS, 201)));
        assertArrayEquals(before, storage.save());
        assertEquals(revision, storage.revision());
        storage.replaceInventoryAndFields(completed, java.util.Map.of(PROGRESS, 0));
        assertEquals(0, storage.get(PROGRESS));
        assertArrayEquals(new byte[0], storage.stack(0));
        assertArrayEquals(new byte[] {9}, storage.stack(3));
        assertEquals(revision + 1, storage.revision());
        var restored = storage();
        restored.restore(storage.save());
        assertEquals(0, restored.get(PROGRESS));
        assertArrayEquals(new byte[] {9}, restored.stack(3));
    }

    @Test void bulkInventoryCommitCopiesAllSlotsAndMarksOneRevision() {
        var storage = storage();
        byte[] first = {42};
        storage.replaceInventory(java.util.List.of(first, new byte[0], new byte[] {7}, new byte[0]));
        first[0] = 99;
        assertArrayEquals(new byte[] {42}, storage.stack(0));
        assertArrayEquals(new byte[] {7}, storage.stack(2));
        assertEquals(1, storage.revision());
        assertTrue(storage.dirty());
    }

    @Test void invalidLastSlotCannotPartiallyCommitEarlierSlots() {
        var storage = storage();
        storage.stack(0, new byte[] {8});
        byte[] before = storage.save();
        long revision = storage.revision();
        assertThrows(IllegalArgumentException.class, () -> storage.replaceInventory(java.util.List.of(
                new byte[] {1}, new byte[0], new byte[0],
                new byte[PortableBlockEntityStorage.MAXIMUM_STACK_BYTES + 1])));
        assertArrayEquals(before, storage.save());
        assertEquals(revision, storage.revision());
        assertThrows(IllegalArgumentException.class, () -> storage.replaceInventory(java.util.List.of()));
        assertArrayEquals(before, storage.save());
    }
    private static final BlockEntityInt PROGRESS = new BlockEntityInt("progress", 0, 0, 200);
    private static final BlockRef BLOCK = new BlockRef(ResourceId.of("test", "machine"));
    // Mutable opaque item bytes stand in for native stacks, including components/NBT.
    private static final BlockEntityStackCodec<byte[]> CODEC = new BlockEntityStackCodec<>() {
        public byte[] empty() { return new byte[0]; }
        public byte[] copy(byte[] stack) { return stack.clone(); }
        public byte[] encode(byte[] stack) { return stack.clone(); }
        public byte[] decode(byte[] encoded) { return encoded.clone(); }
    };

    private PortableBlockEntityStorage<byte[]> storage() {
        return new PortableBlockEntityStorage<>(BlockEntitySpec.builder(BLOCK)
                .inventorySlots(4).field(PROGRESS).build(), CODEC);
    }

    @Test void inventoryAndProgressSurviveSnapshotWithoutLosingOpaqueItemData() {
        var original = storage();
        byte[] components = {1, 64, 0, -1, 42};
        original.stack(0, components);
        original.set(PROGRESS, 83);
        var restored = storage();
        restored.restore(original.save());
        assertArrayEquals(components, restored.stack(0));
        assertEquals(83, restored.get(PROGRESS));
        assertArrayEquals(original.save(), restored.save());
        assertArrayEquals(new byte[0], restored.stack(3));
    }

    @Test void callerCannotMutateOwnedInventoryThroughReferences() {
        var storage = storage();
        byte[] incoming = {7};
        storage.stack(0, incoming);
        long revision = storage.revision();
        incoming[0] = 8;
        storage.stack(0)[0] = 9;
        assertArrayEquals(new byte[] {7}, storage.stack(0));
        assertEquals(revision, storage.revision());
    }

    @Test void dirtyTrackingDoesNotAcknowledgeStaleSaves() {
        var storage = storage();
        assertFalse(storage.dirty());
        storage.set(PROGRESS, 1);
        long savedRevision = storage.revision();
        storage.save();
        assertTrue(storage.dirty());
        storage.set(PROGRESS, 1);
        assertEquals(savedRevision, storage.revision());
        storage.set(PROGRESS, 2);
        assertThrows(IllegalStateException.class, () -> storage.markPersisted(savedRevision));
        assertTrue(storage.dirty());
        storage.markPersisted(storage.revision());
        assertFalse(storage.dirty());
    }

    @Test void malformedSnapshotsNeverPartiallyOverwriteStorage() {
        var storage = storage();
        storage.set(PROGRESS, 91);
        storage.stack(2, new byte[] {99});
        byte[] before = storage.save();
        long revision = storage.revision();
        for (int length = 0; length < before.length; length++) {
            byte[] truncated = Arrays.copyOf(before, length);
            assertThrows(IllegalArgumentException.class, () -> storage.restore(truncated));
            assertArrayEquals(before, storage.save());
            assertEquals(revision, storage.revision());
        }
        assertThrows(IllegalArgumentException.class,
                () -> storage.restore(Arrays.copyOf(before, before.length + 1)));
        assertArrayEquals(before, storage.save());
    }

    @Test void rejectsInvalidFieldsAndOversizedStacksBeforeMutation() {
        var storage = storage();
        assertThrows(IllegalArgumentException.class, () -> storage.set(PROGRESS, 201));
        assertThrows(IllegalArgumentException.class, () -> storage.set(
                new BlockEntityInt("progress", 0, 0, 1000), 2));
        assertThrows(IllegalArgumentException.class, () -> storage.stack(0,
                new byte[PortableBlockEntityStorage.MAXIMUM_STACK_BYTES + 1]));
        assertEquals(0, storage.revision());
        assertEquals(0, storage.get(PROGRESS));
    }

    @Test void requiresMigrationForWrongBlockOrChangedInventorySize() {
        byte[] save = storage().save();
        var wrongBlock = new PortableBlockEntityStorage<>(BlockEntitySpec.builder(
                new BlockRef(ResourceId.of("test", "other"))).inventorySlots(4).field(PROGRESS).build(), CODEC);
        var wrongSize = new PortableBlockEntityStorage<>(BlockEntitySpec.builder(BLOCK)
                .inventorySlots(3).field(PROGRESS).build(), CODEC);
        assertThrows(IllegalArgumentException.class, () -> wrongBlock.restore(save));
        assertThrows(IllegalArgumentException.class, () -> wrongSize.restore(save));
    }

    @Test void addedFieldsDefaultButRemovedFieldsFailInsteadOfDiscardingData() {
        var old = storage();
        var energy = new BlockEntityInt("energy", 10, 0, 100);
        var expanded = new PortableBlockEntityStorage<>(BlockEntitySpec.builder(BLOCK)
                .inventorySlots(4).field(PROGRESS).field(energy).build(), CODEC);
        expanded.restore(old.save());
        assertEquals(10, expanded.get(energy));
        assertThrows(IllegalArgumentException.class, () -> old.restore(expanded.save()));
    }

    @Test void specIsImmutableAndRejectsDuplicateFieldsAndInvalidBounds() {
        var builder = BlockEntitySpec.builder(BLOCK).field(PROGRESS);
        var first = builder.build();
        builder.inventorySlots(4).field(new BlockEntityInt("energy", 0, 0, 100));
        assertEquals(0, first.inventorySlots());
        assertEquals(1, first.fields().size());
        assertThrows(UnsupportedOperationException.class, () -> first.fields().clear());
        assertThrows(IllegalArgumentException.class, () -> builder.field(PROGRESS));
        assertThrows(IllegalArgumentException.class, () -> builder.inventorySlots(-1));
        assertThrows(IllegalArgumentException.class, () -> new BlockEntityInt("../progress", 0, 0, 200));
        assertThrows(IllegalArgumentException.class, () -> new BlockEntityInt("bad", 201, 0, 200));
    }
}
