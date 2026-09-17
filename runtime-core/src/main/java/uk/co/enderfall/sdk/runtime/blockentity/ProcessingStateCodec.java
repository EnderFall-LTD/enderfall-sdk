package uk.co.enderfall.sdk.runtime.blockentity;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

/** Bounded, versioned saved processing state; independent of the native world's NBT representation. */
public final class ProcessingStateCodec {
    public static final int MAXIMUM_BYTES = 4096;
    private ProcessingStateCodec() { }

    public static byte[] encode(PortableProcessingCycle.State state) {
        Objects.requireNonNull(state, "state");
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeByte(1);
            out.writeByte(state.job() == null ? 0 : 1);
            if (state.job() != null) {
                writeString(out, state.job().recipe().toString());
                writeString(out, state.job().revision());
                out.writeInt(state.job().durationTicks());
                out.writeInt(state.elapsedTicks());
            }
            return bytes.toByteArray();
        } catch (IOException failure) {
            throw new IllegalArgumentException("Cannot encode processing state", failure);
        }
    }

    public static PortableProcessingCycle.State decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length > MAXIMUM_BYTES) throw new IllegalArgumentException("Processing state exceeds byte limit");
        try {
            var in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readUnsignedByte() != 1) throw new IllegalArgumentException("Unsupported processing state version");
            int active = in.readUnsignedByte();
            if (active > 1) throw new IllegalArgumentException("Invalid processing state marker");
            var state = PortableProcessingCycle.State.idle();
            if (active == 1) {
                var job = new PortableProcessingCycle.Job(ResourceId.parse(readString(in)), readString(in), in.readInt());
                state = new PortableProcessingCycle.State(job, in.readInt());
            }
            if (in.available() != 0) throw new IllegalArgumentException("Trailing processing state data");
            return state;
        } catch (IOException failure) {
            throw new IllegalArgumentException("Malformed processing state", failure);
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        if (value.length() > 1024) throw new IllegalArgumentException("Processing string exceeds character limit");
        var encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value));
        if (encoded.remaining() > 1024) throw new IllegalArgumentException("Processing string exceeds byte limit");
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > 1024 || length > in.available()) throw new IllegalArgumentException("Invalid processing string length");
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(in.readNBytes(length))).toString();
    }
}
