package uk.co.enderfall.sdk.runtime.blockentity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import uk.co.enderfall.sdk.api.blockentity.BlockEntityInt;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;
import uk.co.enderfall.sdk.api.blockentity.BlockEntityState;

/**
 * Block-owned inventory and saved fields, independent of open menus. Native hooks must
 * store {@link #save()} in world data and call {@link #restore(byte[])} on load.
 * Not thread-safe: all access belongs on the owning game thread. This class does not
 * implement a disk store, menu synchronization, or cross-version item-data conversion.
 */
public final class PortableBlockEntityStorage<S> implements BlockEntityState {
    private static final int MAGIC = 0x45464245; // EFBE
    private static final int FORMAT = 1;
    public static final int MAXIMUM_STACK_BYTES = 65_536;
    public static final int MAXIMUM_SAVE_BYTES = 8 * 1024 * 1024;
    private final BlockEntitySpec spec;
    private final BlockEntityStackCodec<S> codec;
    private List<S> inventory;
    private Map<String, Integer> values;
    private long revision;
    private long persistedRevision;
    private final Map<String, uk.co.enderfall.sdk.runtime.fluid.PortableFluidTank> tanks = new LinkedHashMap<>();

    public PortableBlockEntityStorage(BlockEntitySpec spec, BlockEntityStackCodec<S> codec) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.codec = Objects.requireNonNull(codec, "codec");
        inventory = new ArrayList<>(spec.inventorySlots());
        for (int slot = 0; slot < spec.inventorySlots(); slot++) {
            inventory.add(copy(codec.empty()));
        }
        values = defaults();
        spec.tanks().forEach((name, tank) -> tanks.put(name,
                new uk.co.enderfall.sdk.runtime.fluid.PortableFluidTank(tank.capacity())));
    }

    public int size() { return inventory.size(); }

    /** Internal atomic transfer after native ownership and sided-access checks. */
    public long transferTank(uk.co.enderfall.sdk.api.fluid.FluidTankSpec source,
            PortableBlockEntityStorage<?> destination, uk.co.enderfall.sdk.api.fluid.FluidTankSpec target,
            long maximum, boolean simulate) {
        Objects.requireNonNull(destination, "destination");
        tank(source);
        destination.tank(target);
        long moved = tanks.get(source.name()).transferTo(destination.tanks.get(target.name()), maximum, simulate);
        if (!simulate && moved > 0) {
            revision++;
            if (destination != this) destination.revision++;
        }
        return moved;
    }

    @Override
    public uk.co.enderfall.sdk.api.fluid.FluidTank tank(uk.co.enderfall.sdk.api.fluid.FluidTankSpec definition) {
        Objects.requireNonNull(definition, "definition");
        if (!definition.equals(spec.tanks().get(definition.name()))) {
            throw new IllegalArgumentException("Undeclared or incompatible tank: " + definition.name());
        }
        var tank = tanks.get(definition.name());
        return new uk.co.enderfall.sdk.api.fluid.FluidTank() {
            public long capacity() { return tank.capacity(); }
            public java.util.Optional<uk.co.enderfall.sdk.api.fluid.FluidVolume> contents() { return tank.contents(); }
            public long fill(uk.co.enderfall.sdk.api.fluid.FluidVolume volume, boolean simulate) {
                long changed = tank.fill(volume, simulate);
                if (!simulate && changed > 0) revision++;
                return changed;
            }
            public long drain(uk.co.enderfall.sdk.api.ResourceId fluid, long maximum, boolean simulate) {
                long changed = tank.drain(fluid, maximum, simulate);
                if (!simulate && changed > 0) revision++;
                return changed;
            }
        };
    }
    public long revision() { return revision; }
    public boolean dirty() { return revision != persistedRevision; }

    /** Call only after the corresponding snapshot has actually been persisted. */
    public void markPersisted(long savedRevision) {
        if (savedRevision != revision) {
            throw new IllegalStateException("Cannot acknowledge a stale block-entity snapshot");
        }
        persistedRevision = savedRevision;
    }

    /** Defensive copy: callers cannot mutate storage without dirty tracking. */
    public S stack(int slot) { return copy(inventory.get(slot)); }

    public void stack(int slot, S stack) {
        Objects.checkIndex(slot, size());
        S owned = copy(stack);
        // Validate serialization and native stack limits before accepting the change.
        encodeStack(owned);
        inventory.set(slot, owned);
        revision++;
    }

    /** Copies and validates a complete native container before committing any slot. */
    public void replaceInventory(List<S> stacks) {
        replaceInventoryAndFields(stacks, Map.of());
    }

    /**
     * Atomically commits a complete inventory and selected saved fields. Used for
     * input consumption, stored output and progress reset as one validated change.
     * Validation/copy failure leaves both inventory and fields unchanged.
     */
    public void replaceInventoryAndFields(List<S> stacks, Map<BlockEntityInt, Integer> updates) {
        replaceInventoryFieldsAndTanks(stacks, updates, Map.of());
    }

    /** Validates all item, field and tank replacements before mutating any storage. */
    public void replaceInventoryFieldsAndTanks(List<S> stacks, Map<BlockEntityInt, Integer> updates,
            Map<String, java.util.Optional<uk.co.enderfall.sdk.api.fluid.FluidVolume>> tankUpdates) {
        var preparedTanks = new LinkedHashMap<String, java.util.Optional<uk.co.enderfall.sdk.api.fluid.FluidVolume>>();
        tankUpdates.forEach((name, value) -> {
            var tank = tanks.get(name);
            if (tank == null) throw new IllegalArgumentException("Unknown machine tank: " + name);
            byte[] encoded = uk.co.enderfall.sdk.runtime.fluid.FluidTankCodec.encode(value);
            preparedTanks.put(name, uk.co.enderfall.sdk.runtime.fluid.FluidTankCodec.decode(encoded, tank.capacity()));
        });
        Objects.requireNonNull(stacks, "stacks");
        Objects.requireNonNull(updates, "updates");
        if (stacks.size() != size()) {
            throw new IllegalArgumentException("Block-entity inventory size mismatch");
        }
        List<S> replacement = new ArrayList<>(size());
        for (S stack : stacks) {
            S owned = copy(stack);
            encodeStack(owned);
            replacement.add(owned);
        }
        Map<String, Integer> replacementValues = new LinkedHashMap<>(values);
        updates.forEach((field, value) -> {
            requireField(Objects.requireNonNull(field, "field"));
            replacementValues.put(field.name(), field.validate(Objects.requireNonNull(value, "value")));
        });
        inventory = replacement;
        values = replacementValues;
        preparedTanks.forEach((name, value) -> tanks.get(name).restore(value));
        revision++;
    }

    @Override
    public int get(BlockEntityInt field) {
        requireField(field);
        return values.get(field.name());
    }

    @Override
    public void set(BlockEntityInt field, int value) {
        requireField(field);
        field.validate(value);
        if (values.get(field.name()) != value) {
            values.put(field.name(), value);
            revision++;
        }
    }

    /** Deterministic envelope; native stack bytes remain opaque and target-specific. */
    public byte[] save() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeInt(tanks.isEmpty() ? FORMAT : 2);
            writeText(out, spec.block().id().toString());
            out.writeInt(values.size());
            for (String name : values.keySet().stream().sorted().toList()) {
                writeText(out, name);
                out.writeInt(values.get(name));
            }
            out.writeInt(size());
            for (S stack : inventory) {
                byte[] encoded = encodeStack(stack);
                if (bytes.size() > MAXIMUM_SAVE_BYTES - 4 - encoded.length) {
                    throw new IllegalArgumentException("Block-entity save exceeds byte limit");
                }
                out.writeInt(encoded.length);
                out.write(encoded);
            }
            if (!tanks.isEmpty()) {
                out.writeInt(tanks.size());
                for (String name : tanks.keySet().stream().sorted().toList()) {
                    writeText(out, name);
                    byte[] encoded = tanks.get(name).save();
                    out.writeInt(encoded.length);
                    out.write(encoded);
                }
            }
            if (bytes.size() > MAXIMUM_SAVE_BYTES) throw new IllegalArgumentException("Block-entity save exceeds byte limit");
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * Validates the entire snapshot before replacing any state. Corrupt/unknown data
     * fails instead of silently emptying inventories. New fields receive their defaults;
     * removed fields, changed slot counts and incompatible formats need explicit migration.
     * Loading does not acknowledge a world save; markPersisted remains the native hook's job.
     */
    public void restore(byte[] snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.length > MAXIMUM_SAVE_BYTES) {
            throw new IllegalArgumentException("Block-entity save exceeds byte limit");
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(snapshot));
            int magic = in.readInt();
            int format = in.readInt();
            if (magic != MAGIC || (format != FORMAT && format != 2)) {
                throw new IllegalArgumentException("Unsupported block-entity save format");
            }
            if (!readText(in, 1024).equals(spec.block().id().toString())) {
                throw new IllegalArgumentException("Block-entity save belongs to a different block");
            }
            int count = in.readInt();
            if (count < 0 || count > BlockEntitySpec.MAXIMUM_FIELDS) {
                throw new IllegalArgumentException("Invalid block-entity field count");
            }
            Map<String, Integer> restoredValues = defaults();
            HashSet<String> seen = new HashSet<>();
            for (int index = 0; index < count; index++) {
                String name = readText(in, 64);
                BlockEntityInt field = spec.fields().get(name);
                if (field == null || !seen.add(name)) {
                    throw new IllegalArgumentException("Unknown or duplicate block-entity field: " + name);
                }
                restoredValues.put(name, field.validate(in.readInt()));
            }
            if (in.readInt() != size()) {
                throw new IllegalArgumentException("Block-entity inventory size changed; migration required");
            }
            List<S> restoredInventory = new ArrayList<>(size());
            for (int slot = 0; slot < size(); slot++) {
                int length = in.readInt();
                if (length < 0 || length > MAXIMUM_STACK_BYTES || length > in.available()) {
                    throw new IllegalArgumentException("Invalid block-entity stack length");
                }
                S stack = copy(codec.decode(in.readNBytes(length)));
                encodeStack(stack);
                restoredInventory.add(stack);
            }
            Map<String, java.util.Optional<uk.co.enderfall.sdk.api.fluid.FluidVolume>> restoredTanks = new LinkedHashMap<>();
            tanks.keySet().forEach(name -> restoredTanks.put(name, java.util.Optional.empty()));
            if (format == 2) {
                int tankCount = in.readInt();
                if (tankCount < 0 || tankCount > 16) throw new IllegalArgumentException("Invalid tank count");
                HashSet<String> tankNames = new HashSet<>();
                for (int index = 0; index < tankCount; index++) {
                    String name = readText(in, 64);
                    var tank = tanks.get(name);
                    if (tank == null || !tankNames.add(name)) throw new IllegalArgumentException("Unknown or duplicate tank");
                    int length = in.readInt();
                    if (length < 0 || length > uk.co.enderfall.sdk.runtime.fluid.FluidTankCodec.MAXIMUM_BYTES
                            || length > in.available()) throw new IllegalArgumentException("Invalid tank snapshot length");
                    restoredTanks.put(name, uk.co.enderfall.sdk.runtime.fluid.FluidTankCodec.decode(in.readNBytes(length), tank.capacity()));
                }
            }
            if (in.available() != 0) {
                throw new IllegalArgumentException("Trailing block-entity save data");
            }
            values = restoredValues;
            inventory = restoredInventory;
            restoredTanks.forEach((name, contents) -> tanks.get(name).restore(contents));
            revision++;
        } catch (IOException failure) {
            throw new IllegalArgumentException("Malformed block-entity save", failure);
        }
    }

    private byte[] encodeStack(S stack) {
        byte[] encoded = Objects.requireNonNull(codec.encode(copy(stack)), "encoded stack");
        if (encoded.length > MAXIMUM_STACK_BYTES) {
            throw new IllegalArgumentException("Block-entity stack exceeds byte limit");
        }
        return encoded;
    }

    private S copy(S stack) {
        return Objects.requireNonNull(codec.copy(Objects.requireNonNull(stack, "stack")), "copied stack");
    }

    private Map<String, Integer> defaults() {
        Map<String, Integer> result = new LinkedHashMap<>();
        spec.fields().forEach((name, field) -> result.put(name, field.defaultValue()));
        return result;
    }

    private void requireField(BlockEntityInt field) {
        Objects.requireNonNull(field, "field");
        if (!field.equals(spec.fields().get(field.name()))) {
            throw new IllegalArgumentException("Undeclared or incompatible block-entity field: " + field.name());
        }
    }

    private static void writeText(DataOutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 1024) throw new IllegalArgumentException("Block-entity key exceeds byte limit");
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readText(DataInputStream in, int limit) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > limit || length > in.available()) {
            throw new IllegalArgumentException("Invalid block-entity key length");
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(in.readNBytes(length))).toString();
        } catch (CharacterCodingException failure) {
            throw new IllegalArgumentException("Malformed block-entity UTF-8", failure);
        }
    }
}
