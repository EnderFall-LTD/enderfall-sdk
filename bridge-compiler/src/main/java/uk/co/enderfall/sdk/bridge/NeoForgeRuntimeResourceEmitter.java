package uk.co.enderfall.sdk.bridge;

import java.util.List;
import java.util.Objects;
import uk.co.enderfall.sdk.bridge.model.MinecraftAbi;
import uk.co.enderfall.sdk.bridge.model.MinecraftVersion;
import uk.co.enderfall.sdk.bridge.model.ModLoader;
import uk.co.enderfall.sdk.bridge.model.ModRuntimeMetadata;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Deterministically renders modern NeoForge runtime resources from typed metadata. */
final class NeoForgeRuntimeResourceEmitter {
    private NeoForgeRuntimeResourceEmitter() {
    }

    static List<RuntimeResource> emit(TargetSpec target, ModRuntimeMetadata runtimeMetadata)
            throws BridgeGenerationException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(runtimeMetadata, "runtimeMetadata");
        if (target.loader() != ModLoader.NEOFORGE
                || (target.minecraftVersion() != MinecraftVersion.V1_21_1
                        && target.minecraftVersion() != MinecraftVersion.V1_21_4
                        && target.minecraftVersion() != MinecraftVersion.V26_2)) {
            throw new BridgeGenerationException(
                    "NeoForge runtime resource emission is not implemented for target " + target.id());
        }
        return List.of(
                new RuntimeResource("META-INF/neoforge.mods.toml", modsToml(target, runtimeMetadata)),
                new RuntimeResource("pack.mcmeta", packMetadata(target, runtimeMetadata)));
    }

    private static String modsToml(TargetSpec target, ModRuntimeMetadata metadata) {
        return "modLoader=\"javafml\"\n"
                + "loaderVersion=\"[" + (target.minecraftVersion() == MinecraftVersion.V26_2 ? 10 : 4) + ",)\"\n"
                + "license=\"" + tomlString(metadata.license()) + "\"\n"
                + "\n"
                + "[[mods]]\n"
                + "modId=\"" + tomlString(metadata.id()) + "\"\n"
                + "version=\"" + tomlString(metadata.versionPlaceholder()) + "\"\n"
                + "displayName=\"" + tomlString(metadata.name()) + "\"\n"
                + "authors=\"" + tomlString(String.join(", ", metadata.authors())) + "\"\n"
                + "description='''Portable mod runtime for Minecraft "
                + target.minecraftVersion().id() + " on NeoForge.'''\n"
                + "\n"
                + "[[dependencies." + metadata.id() + "]]\n"
                + "modId=\"neoforge\"\n"
                + "type=\"required\"\n"
                + "versionRange=\"[" + tomlString(target.loaderVersion()) + ",)\"\n"
                + "ordering=\"NONE\"\n"
                + "side=\"BOTH\"\n"
                + "\n"
                + "[[dependencies." + metadata.id() + "]]\n"
                + "modId=\"minecraft\"\n"
                + "type=\"required\"\n"
                + "versionRange=\"[" + tomlString(target.minecraftVersion().id()) + "]\"\n"
                + "ordering=\"NONE\"\n"
                + "side=\"BOTH\"\n";
    }

    private static String packMetadata(TargetSpec target, ModRuntimeMetadata metadata)
            throws BridgeGenerationException {
        MinecraftAbi.ResourcePackFormat packFormat = target.minecraftAbi().resourcePackFormat();
        if (packFormat instanceof MinecraftAbi.IntegerRangePackFormat format) {
            return "{\n"
                    + "  \"pack\": {\n"
                    + "    \"pack_format\": " + format.packFormat() + ",\n"
                    + "    \"supported_formats\": [" + format.minimumSupportedFormat() + ", "
                    + format.maximumSupportedFormat() + "],\n"
                    + "    \"description\": \"" + jsonString(metadata.resourcePackDescription()) + "\"\n"
                    + "  }\n"
                    + "}\n";
        }
        if (packFormat instanceof MinecraftAbi.VersionedRangePackFormat format) {
            return "{\n"
                    + "  \"pack\": {\n"
                    + "    \"min_format\": " + format.minimumFormat() + ",\n"
                    + "    \"max_format\": [" + format.maximumFormatMajor() + ", "
                    + format.maximumFormatMinor() + "],\n"
                    + "    \"description\": \"" + jsonString(metadata.resourcePackDescription()) + "\"\n"
                    + "  }\n"
                    + "}\n";
        }
        throw new BridgeGenerationException(
                "NeoForge pack metadata emission does not support the format for " + target.id());
    }

    private static String tomlString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String jsonString(String value) {
        return tomlString(value).replace("\b", "\\b").replace("\f", "\\f")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
