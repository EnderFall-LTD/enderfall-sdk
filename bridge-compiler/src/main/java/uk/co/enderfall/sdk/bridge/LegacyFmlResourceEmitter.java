package uk.co.enderfall.sdk.bridge;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.MinecraftAbi;
import uk.co.enderfall.sdk.bridge.model.ModLoader;
import uk.co.enderfall.sdk.bridge.model.ModRuntimeMetadata;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Shared legacy FML metadata format; only coordinates and loader identity vary. */
final class LegacyFmlResourceEmitter {
    private LegacyFmlResourceEmitter() {
    }

    static List<RuntimeResource> emit(TargetSpec target, ModRuntimeMetadata metadata)
            throws BridgeGenerationException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(metadata, "metadata");
        if (target.loaderAbi() != LoaderAbi.LEGACY_FML
                || !(target.minecraftAbi().resourcePackFormat() instanceof MinecraftAbi.SinglePackFormat format)) {
            throw new BridgeGenerationException("Legacy FML resource emission does not support " + target.id());
        }
        return List.of(new RuntimeResource("META-INF/mods.toml", modsToml(target, metadata)),
                new RuntimeResource("META-INF/enderfall-sdk-loader", target.loader().id() + '\n'),
                new RuntimeResource("pack.mcmeta", "{\n"
                        + "  \"pack\": {\n"
                        + "    \"pack_format\": " + format.packFormat() + ",\n"
                        + "    \"description\": \"" + escape(metadata.resourcePackDescription()) + "\"\n"
                        + "  }\n"
                        + "}\n"));
    }

    private static String modsToml(TargetSpec target, ModRuntimeMetadata metadata) {
        String displayLoader = target.loader() == ModLoader.FORGE ? "Forge" : "NeoForge";
        return "modLoader=\"javafml\"\n"
                + "loaderVersion=\"[47,)\"\n"
                + "license=\"" + escape(metadata.license()) + "\"\n\n"
                + "[[mods]]\n"
                + "modId=\"" + metadata.id() + "\"\n"
                + "version=\"" + escape(metadata.versionPlaceholder()) + "\"\n"
                + "displayName=\"" + escape(metadata.name()) + "\"\n"
                + "authors=\"" + escape(String.join(", ", metadata.authors())) + "\"\n"
                + "description='''Portable mod runtime for Minecraft " + target.minecraftVersion().id()
                + " on " + displayLoader + ".'''\n\n"
                // Both 47.x loaders expose the forge mod/container identity.
                + "[[dependencies." + metadata.id() + "]]\n"
                + "modId=\"forge\"\n"
                + "mandatory=true\n"
                + "versionRange=\"[" + escape(target.loaderVersion()) + ",)\"\n"
                + "ordering=\"NONE\"\n"
                + "side=\"BOTH\"\n\n"
                + "[[dependencies." + metadata.id() + "]]\n"
                + "modId=\"minecraft\"\n"
                + "mandatory=true\n"
                + "versionRange=\"[" + target.minecraftVersion().id() + "]\"\n"
                + "ordering=\"NONE\"\n"
                + "side=\"BOTH\"\n";
    }

    private static String escape(String text) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20 || character == 0x7f) {
                        result.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        return result.toString();
    }
}
