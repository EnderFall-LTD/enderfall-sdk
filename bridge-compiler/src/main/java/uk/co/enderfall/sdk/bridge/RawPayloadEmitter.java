package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.MinecraftVersion;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** One bounded, defensively copied native payload codec for all modern targets. */
final class RawPayloadEmitter {
    private RawPayloadEmitter() { }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> paths) throws BridgeGenerationException {
        if (!TargetCatalog.standard().require(target.id()).equals(target)) throw new BridgeGenerationException("Unreviewed payload target " + target.id());
        if (target.minecraftVersion() == MinecraftVersion.V1_20_1) return List.of();
        boolean fabric = target.loaderAbi() == LoaderAbi.FABRIC;
        String loader = fabric ? "fabric" : "neoforge";
        String prefix = fabric ? "Fabric" : "NeoForge";
        String path = "uk/co/enderfall/sdk/runtime/" + loader + "/v1_21_4/" + prefix + "RawPayload.java";
        if (!paths.contains(path)) return List.of();
        return List.of(new RuntimeSource(path, path,
                PAYLOAD.formatted(loader, prefix, fabric ? "" : "\n            ").getBytes(StandardCharsets.UTF_8)));
    }

    private static final String PAYLOAD = """
            package uk.co.enderfall.sdk.runtime.%1$s.v1_21_4;
            
            import java.util.Arrays;
            import net.minecraft.network.RegistryFriendlyByteBuf;
            import net.minecraft.network.codec.StreamCodec;
            import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
            
            record %2$sRawPayload(Type<%2$sRawPayload> type, byte[] bytes) implements CustomPacketPayload {
                %2$sRawPayload {
                    bytes = Arrays.copyOf(bytes, bytes.length);
                }
            
                @Override
                public byte[] bytes() {
                    return Arrays.copyOf(bytes, bytes.length);
                }
            
                static StreamCodec<RegistryFriendlyByteBuf, %2$sRawPayload> codec(%3$sType<%2$sRawPayload> type, int maximumBytes) {
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
                                return new %2$sRawPayload(type, bytes);
                            });
                }
            }
            """;
}
