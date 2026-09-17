package uk.co.enderfall.sdk.bridge;

import java.util.List;
import java.util.Objects;
import uk.co.enderfall.sdk.bridge.model.MinecraftAbi;
import uk.co.enderfall.sdk.bridge.model.MinecraftVersion;
import uk.co.enderfall.sdk.bridge.model.ModLoader;
import uk.co.enderfall.sdk.bridge.model.ModRuntimeMetadata;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Deterministically renders Fabric runtime resources from typed metadata. */
final class FabricRuntimeResourceEmitter {
    private static final int FABRIC_SCHEMA_VERSION = 1;
    private FabricRuntimeResourceEmitter() {
    }

    static List<RuntimeResource> emit(TargetSpec target, ModRuntimeMetadata runtimeMetadata)
            throws BridgeGenerationException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(runtimeMetadata, "runtimeMetadata");
        if (target.loader() != ModLoader.FABRIC
                || (target.minecraftVersion() != MinecraftVersion.V1_20_1
                        && target.minecraftVersion() != MinecraftVersion.V1_21_1
                        && target.minecraftVersion() != MinecraftVersion.V1_21_4
                        && target.minecraftVersion() != MinecraftVersion.V26_2)) {
            throw new BridgeGenerationException(
                    "Fabric runtime resource emission is not implemented for target " + target.id());
        }
        if (target.platformApiVersion().isBlank()) {
            throw new BridgeGenerationException(target.id() + " must declare an exact Fabric API version");
        }
        return List.of(
                new RuntimeResource("fabric.mod.json", fabricModJson(target, runtimeMetadata)),
                new RuntimeResource("pack.mcmeta", packMetadata(target, runtimeMetadata)));
    }

    private static String fabricModJson(TargetSpec target, ModRuntimeMetadata metadata) {
        boolean legacy = target.minecraftAbi() == MinecraftAbi.V1_20_1;
        String loaderName = switch (target.loader()) {
            case FABRIC -> "Fabric";
            case FORGE -> "Forge";
            case NEOFORGE -> "NeoForge";
        };
        String authors = metadata.authors().stream()
                .map(FabricRuntimeResourceEmitter::jsonString)
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow();
        return "{\n"
                + "  \"schemaVersion\": " + FABRIC_SCHEMA_VERSION + ",\n"
                + "  \"id\": " + jsonString(metadata.id()) + ",\n"
                + "  \"version\": " + jsonString(metadata.versionPlaceholder()) + ",\n"
                + "  \"name\": " + jsonString(metadata.name()) + ",\n"
                + "  \"description\": " + jsonString("Portable mod runtime for Minecraft "
                        + target.minecraftVersion().id() + " on " + loaderName + (legacy ? "." : "")) + ",\n"
                + "  \"authors\": [" + authors + "],\n"
                + "  \"license\": " + jsonString(metadata.license()) + ",\n"
                + "  \"environment\": " + jsonString(metadata.environment()) + ",\n"
                + "  \"entrypoints\": {\n"
                + (legacy ? "    \"main\": [" + jsonString(metadata.fabricMainEntrypoint()) + "]\n"
                        : "    \"main\": [\n"
                + "      " + jsonString(metadata.fabricMainEntrypoint()) + "\n"
                + "    ]\n")
                + "  },\n"
                + "  \"depends\": {\n"
                + "    \"fabricloader\": " + jsonString(">=" + target.loaderVersion()) + ",\n"
                + (legacy ? "" : "    \"fabric-api\": " + jsonString(">=" + target.platformApiVersion()) + ",\n")
                + "    \"minecraft\": " + jsonString("=" + target.minecraftVersion().id()) + ",\n"
                + "    \"java\": " + jsonString(">=" + target.javaVersion()) + "\n"
                + "  }\n"
                + "}\n";
    }

    private static String packMetadata(TargetSpec target, ModRuntimeMetadata metadata)
            throws BridgeGenerationException {
        MinecraftAbi.ResourcePackFormat packFormat = target.minecraftAbi().resourcePackFormat();
        if (packFormat instanceof MinecraftAbi.SinglePackFormat format) {
            return "{\n"
                    + "  \"pack\": {\n"
                    + "    \"pack_format\": " + format.packFormat() + ",\n"
                    + "    \"description\": " + jsonString(metadata.resourcePackDescription()) + "\n"
                    + "  }\n"
                    + "}\n";
        }
        if (packFormat instanceof MinecraftAbi.IntegerRangePackFormat format) {
            return "{\n"
                    + "  \"pack\": {\n"
                    + "    \"pack_format\": " + format.packFormat() + ",\n"
                    + "    \"supported_formats\": [" + format.minimumSupportedFormat() + ", "
                            + format.maximumSupportedFormat() + "],\n"
                    + "    \"description\": " + jsonString(metadata.resourcePackDescription()) + "\n"
                    + "  }\n"
                    + "}\n";
        }
        if (packFormat instanceof MinecraftAbi.VersionedRangePackFormat format) {
            return "{\n"
                    + "  \"pack\": {\n"
                    + "    \"min_format\": " + format.minimumFormat() + ",\n"
                    + "    \"max_format\": [" + format.maximumFormatMajor() + ", "
                            + format.maximumFormatMinor() + "],\n"
                    + "    \"description\": " + jsonString(metadata.resourcePackDescription()) + "\n"
                    + "  }\n"
                    + "}\n";
        }
        throw new BridgeGenerationException(
                "Fabric pack metadata emission does not support the format for " + target.id());
    }

    private static String jsonString(String value) {
        StringBuilder output = new StringBuilder(value.length() + 2).append('"');
        value.codePoints().forEach(codePoint -> appendJsonCodePoint(output, codePoint));
        return output.append('"').toString();
    }

    private static void appendJsonCodePoint(StringBuilder output, int codePoint) {
        switch (codePoint) {
            case '"' -> output.append("\\\"");
            case '\\' -> output.append("\\\\");
            case '\b' -> output.append("\\b");
            case '\f' -> output.append("\\f");
            case '\n' -> output.append("\\n");
            case '\r' -> output.append("\\r");
            case '\t' -> output.append("\\t");
            default -> {
                if (codePoint < 0x20) {
                    output.append(String.format(java.util.Locale.ROOT, "\\u%04x", codePoint));
                } else {
                    output.appendCodePoint(codePoint);
                }
            }
        }
    }
}
