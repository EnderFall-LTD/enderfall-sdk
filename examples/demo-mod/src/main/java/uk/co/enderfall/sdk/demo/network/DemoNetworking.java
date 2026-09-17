package uk.co.enderfall.sdk.demo.network;

import java.util.UUID;
import uk.co.enderfall.sdk.api.ModContext;
import uk.co.enderfall.sdk.api.network.PacketCodec;
import uk.co.enderfall.sdk.api.network.PacketDirection;
import uk.co.enderfall.sdk.api.network.PacketReader;
import uk.co.enderfall.sdk.api.network.PacketRequirement;
import uk.co.enderfall.sdk.api.network.PacketType;
import uk.co.enderfall.sdk.api.network.PacketWriter;

/** A typed packet registered once and handled on both logical sides. */
public final class DemoNetworking {
    private final ModContext context;
    private final PacketType<ResonancePulse> pulse;

    private DemoNetworking(ModContext context, PacketType<ResonancePulse> pulse) {
        this.context = context;
        this.pulse = pulse;
    }

    public static DemoNetworking register(ModContext context) {
        PacketType<ResonancePulse> pulse = new PacketType<>(context.id("resonance_pulse"), 1,
                PacketDirection.BIDIRECTIONAL, PacketRequirement.OPTIONAL, 1_024, new ResonancePulseCodec());
        context.networking().register(pulse, (message, network) -> {
            context.logger().info("Received resonance pulse '{}' at strength {} from {}",
                    message.message(), message.strength(), network.receivedDirection());
            if (network.receivedDirection() == PacketDirection.CLIENTBOUND) {
                context.networking().sendToServer(pulse,
                        new ResonancePulse("client acknowledged: " + message.message(), message.strength()));
            }
        });
        return new DemoNetworking(context, pulse);
    }

    public boolean sendPulse(UUID playerId, String message, int strength) {
        if (!context.networking().remoteSupports(playerId, pulse)) {
            return false;
        }
        context.networking().sendToPlayer(playerId, pulse, new ResonancePulse(message, strength));
        return true;
    }

    public record ResonancePulse(String message, int strength) {
    }

    private static final class ResonancePulseCodec implements PacketCodec<ResonancePulse> {
        @Override
        public void encode(PacketWriter writer, ResonancePulse value) {
            writer.writeString(value.message(), 512);
            writer.writeVarInt(value.strength());
        }

        @Override
        public ResonancePulse decode(PacketReader reader) {
            return new ResonancePulse(reader.readString(512), reader.readVarInt());
        }
    }
}
