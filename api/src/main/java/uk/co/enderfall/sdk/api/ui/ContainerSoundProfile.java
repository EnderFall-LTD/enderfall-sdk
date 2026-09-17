package uk.co.enderfall.sdk.api.ui;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;

/** Sounds emitted when the first viewer opens and the last viewer closes a container. */
public record ContainerSoundProfile(ResourceId open, ResourceId close, float volume, float pitch) {
    public static final ContainerSoundProfile NONE = new ContainerSoundProfile(null, null, 0.0F, 1.0F);
    public static final ContainerSoundProfile CHEST = vanilla("block.chest.open", "block.chest.close", 0.5F, 1.0F);
    public static final ContainerSoundProfile BARREL = vanilla("block.barrel.open", "block.barrel.close", 0.5F, 1.0F);

    public ContainerSoundProfile {
        if ((open == null) != (close == null)) {
            throw new IllegalArgumentException("Container open and close sounds must both be present or absent");
        }
        if (!Float.isFinite(volume) || volume < 0.0F || volume > 16.0F) {
            throw new IllegalArgumentException("Container sound volume must be between 0 and 16");
        }
        if (!Float.isFinite(pitch) || pitch <= 0.0F || pitch > 4.0F) {
            throw new IllegalArgumentException("Container sound pitch must be greater than 0 and at most 4");
        }
    }

    public static ContainerSoundProfile of(ResourceId open, ResourceId close, float volume, float pitch) {
        return new ContainerSoundProfile(Objects.requireNonNull(open, "open"),
                Objects.requireNonNull(close, "close"), volume, pitch);
    }

    public boolean enabled() {
        return open != null;
    }

    private static ContainerSoundProfile vanilla(String open, String close, float volume, float pitch) {
        return of(ResourceId.of("minecraft", open), ResourceId.of("minecraft", close), volume, pitch);
    }
}
