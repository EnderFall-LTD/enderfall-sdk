package uk.co.enderfall.sdk.api.network;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

public record PacketType<T>(ResourceId id, int schemaVersion, PacketDirection direction,
                            PacketRequirement requirement, int maximumBytes, PacketCodec<T> codec) {
    public static final int SDK_MAXIMUM_BYTES = 32_000;

    public PacketType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(codec, "codec");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        if (maximumBytes < 1 || maximumBytes > SDK_MAXIMUM_BYTES) {
            throw new IllegalArgumentException("maximumBytes must be between 1 and " + SDK_MAXIMUM_BYTES);
        }
    }
}
