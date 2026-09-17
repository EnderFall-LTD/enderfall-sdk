package uk.co.enderfall.sdk.runtime.blockentity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import uk.co.enderfall.sdk.api.render.AnimationPlaybackState;

/** Versioned bounded visual descriptor. Null is a complete reset, not a partial update. */
public final class RenderAnimationSnapshot {
    public static final int MAXIMUM_BYTES = 83;
    private RenderAnimationSnapshot() { }
    public static byte[] encode(AnimationPlaybackState state) {
        if (state == null) return new byte[] {1, 0};
        byte[] name = state.animation().getBytes(StandardCharsets.US_ASCII);
        var out = ByteBuffer.allocate(3 + name.length + 16);
        out.put((byte) 1).put((byte) 1).put((byte) name.length).put(name)
                .putLong(state.startTick()).putLong(state.stopTick() == null ? -1 : state.stopTick());
        return out.array();
    }
    public static AnimationPlaybackState decode(byte[] bytes, Set<String> names) {
        if (bytes.length < 2 || bytes.length > MAXIMUM_BYTES) throw new IllegalArgumentException("Invalid animation payload size");
        var in = ByteBuffer.wrap(bytes);
        if (in.get() != 1) throw new IllegalArgumentException("Unsupported animation snapshot version");
        int present = in.get();
        if (present == 0 && !in.hasRemaining()) return null;
        if (present != 1 || in.remaining() < 17) throw new IllegalArgumentException("Invalid animation descriptor");
        int length = Byte.toUnsignedInt(in.get());
        if (length < 1 || length > 64 || in.remaining() != length + 16) throw new IllegalArgumentException("Invalid animation name length");
        byte[] name = new byte[length]; in.get(name);
        String id = new String(name, StandardCharsets.US_ASCII);
        if (!names.contains(id)) throw new IllegalArgumentException("Undeclared animation: " + id);
        long start = in.getLong(); long stop = in.getLong();
        if (stop < -1) throw new IllegalArgumentException("Invalid animation stop tick");
        return new AnimationPlaybackState(id, start, stop == -1 ? null : stop);
    }
}
