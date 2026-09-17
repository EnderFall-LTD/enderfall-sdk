package uk.co.enderfall.sdk.runtime.blockentity;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;
import uk.co.enderfall.sdk.api.fluid.FluidVolume;
import uk.co.enderfall.sdk.runtime.fluid.FluidTankCodec;

/** Immutable, complete replacement of explicitly public tank visuals. No inventory or saved fields. */
public final class RenderTankSnapshot {
    public static final int MAXIMUM_BYTES = 18000;
    private static final int MAGIC = 0x45465254;
    private final Map<String, Optional<FluidVolume>> contents;
    private RenderTankSnapshot(Map<String, Optional<FluidVolume>> contents) {
        this.contents = Collections.unmodifiableMap(new TreeMap<>(contents));
    }
    public Optional<FluidVolume> contents(String name) {
        if (!contents.containsKey(name)) throw new IllegalArgumentException("Tank is not exposed for rendering: " + name);
        return contents.get(name);
    }
    public static RenderTankSnapshot capture(BlockEntitySpec spec, Function<String, Optional<FluidVolume>> source) {
        Objects.requireNonNull(spec, "spec"); Objects.requireNonNull(source, "source");
        var result = new TreeMap<String, Optional<FluidVolume>>();
        for (String name : spec.renderTanks()) {
            var value = Objects.requireNonNull(source.apply(name), "contents");
            if (value.isPresent() && value.get().amount() > spec.tanks().get(name).capacity()) {
                throw new IllegalArgumentException("Visual amount exceeds tank capacity");
            }
            result.put(name, value);
        }
        return new RenderTankSnapshot(result);
    }
    public byte[] encode() {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeInt(MAGIC); out.writeByte(1); out.writeByte(contents.size());
            for (var entry : contents.entrySet()) {
                byte[] name = entry.getKey().getBytes(StandardCharsets.US_ASCII);
                byte[] value = FluidTankCodec.encode(entry.getValue());
                if (bytes.size() + 3 + name.length + value.length > MAXIMUM_BYTES) throw new IllegalArgumentException("Visual tanks exceed byte limit");
                out.writeByte(name.length); out.write(name); out.writeShort(value.length); out.write(value);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    public static RenderTankSnapshot decode(byte[] bytes, BlockEntitySpec spec) {
        Objects.requireNonNull(bytes, "bytes"); Objects.requireNonNull(spec, "spec");
        if (bytes.length < 6 || bytes.length > MAXIMUM_BYTES) throw new IllegalArgumentException("Invalid visual tank size");
        try {
            var in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != MAGIC || in.readUnsignedByte() != 1 || in.readUnsignedByte() != spec.renderTanks().size()) {
                throw new IllegalArgumentException("Invalid visual tank schema");
            }
            var result = new TreeMap<String, Optional<FluidVolume>>();
            for (String expected : spec.renderTanks()) {
                int length = in.readUnsignedByte();
                if (length < 1 || length > 64 || length > in.available()) throw new IllegalArgumentException("Invalid tank name length");
                byte[] encodedName = in.readNBytes(length);
                if (!Arrays.equals(encodedName, expected.getBytes(StandardCharsets.US_ASCII))) throw new IllegalArgumentException("Unexpected visual tank");
                int size = in.readUnsignedShort();
                if (size > FluidTankCodec.MAXIMUM_BYTES || size > in.available()) throw new IllegalArgumentException("Invalid tank data length");
                result.put(expected, FluidTankCodec.decode(in.readNBytes(size), spec.tanks().get(expected).capacity()));
            }
            if (in.available() != 0) throw new IllegalArgumentException("Trailing visual tank bytes");
            return new RenderTankSnapshot(result);
        } catch (IOException invalid) { throw new IllegalArgumentException("Truncated visual tanks", invalid); }
    }
}
