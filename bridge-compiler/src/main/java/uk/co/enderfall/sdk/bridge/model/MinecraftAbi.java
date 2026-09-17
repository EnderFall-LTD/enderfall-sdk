package uk.co.enderfall.sdk.bridge.model;

/** Minecraft native API families understood by the bridge compiler. */
public enum MinecraftAbi {
    V1_20_1(new SinglePackFormat(15)),
    V1_21_1(new IntegerRangePackFormat(34, 34, 48)),
    V1_21_4(new IntegerRangePackFormat(46, 46, 61)),
    V26_2(new VersionedRangePackFormat(88, 107, 1));

    private final ResourcePackFormat resourcePackFormat;

    MinecraftAbi(ResourcePackFormat resourcePackFormat) {
        this.resourcePackFormat = resourcePackFormat;
    }

    /** Returns the exact resource-pack metadata format used by this Minecraft ABI. */
    public ResourcePackFormat resourcePackFormat() {
        return resourcePackFormat;
    }

    /** Typed resource-pack metadata shapes used across supported Minecraft generations. */
    public sealed interface ResourcePackFormat
            permits SinglePackFormat, IntegerRangePackFormat, VersionedRangePackFormat {
    }

    /** Legacy metadata containing only one integer {@code pack_format}. */
    public record SinglePackFormat(int packFormat) implements ResourcePackFormat {
    }

    /** Metadata containing an integer format plus an inclusive integer supported range. */
    public record IntegerRangePackFormat(
            int packFormat,
            int minimumSupportedFormat,
            int maximumSupportedFormat) implements ResourcePackFormat {
    }

    /** 26.x metadata containing a minimum format and a major/minor maximum format. */
    public record VersionedRangePackFormat(
            int minimumFormat,
            int maximumFormatMajor,
            int maximumFormatMinor) implements ResourcePackFormat {
    }
}
