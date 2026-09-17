package uk.co.enderfall.sdk.runtime.fabric.v1_21_4;

import java.util.Arrays;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

record FabricRawPayload(Type<FabricRawPayload> type, byte[] bytes) implements CustomPacketPayload {
    FabricRawPayload {
        bytes = Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    static StreamCodec<RegistryFriendlyByteBuf, FabricRawPayload> codec(Type<FabricRawPayload> type, int maximumBytes) {
        return StreamCodec.of(
                (buffer, payload) -> {
                    if (payload.bytes.length > maximumBytes) {
                        throw new IllegalArgumentException("Payload exceeds " + maximumBytes + " bytes");
                    }
                    buffer.writeBytes(payload.bytes);
                },
                buffer -> {
                    int length = buffer.readableBytes();
                    if (length > maximumBytes) {
                        throw new IllegalArgumentException("Payload exceeds " + maximumBytes + " bytes");
                    }
                    byte[] bytes = new byte[length];
                    buffer.readBytes(bytes);
                    return new FabricRawPayload(type, bytes);
                });
    }
}
