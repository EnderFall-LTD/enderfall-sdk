package uk.co.enderfall.sdk.bridge.model;

import java.util.Objects;
import java.util.regex.Pattern;

/** Complete, typed build and native-ABI description for one supported target. */
public record TargetSpec(
        String id,
        MinecraftVersion minecraftVersion,
        ModLoader loader,
        int javaVersion,
        String loaderVersion,
        String platformApiVersion,
        LoaderAbi loaderAbi,
        MinecraftAbi minecraftAbi,
        MappingAbi mappingAbi,
        RecipeAbi recipeAbi,
        NetworkAbi networkAbi,
        MenuAbi menuAbi) {

    private static final Pattern SAFE_ID = Pattern.compile("[0-9]+(?:\\.[0-9]+){1,2}-(?:fabric|forge|neoforge)");

    /** Validates a target description at construction time. */
    public TargetSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(loaderVersion, "loaderVersion");
        Objects.requireNonNull(platformApiVersion, "platformApiVersion");
        Objects.requireNonNull(loaderAbi, "loaderAbi");
        Objects.requireNonNull(minecraftAbi, "minecraftAbi");
        Objects.requireNonNull(mappingAbi, "mappingAbi");
        Objects.requireNonNull(recipeAbi, "recipeAbi");
        Objects.requireNonNull(networkAbi, "networkAbi");
        Objects.requireNonNull(menuAbi, "menuAbi");

        String expectedId = minecraftVersion.id() + '-' + loader.id();
        if (!SAFE_ID.matcher(id).matches() || !id.equals(expectedId)) {
            throw new IllegalArgumentException("Target ID must be exactly " + expectedId + ": " + id);
        }
        if (javaVersion != minecraftVersion.javaVersion()) {
            throw new IllegalArgumentException(
                    id + " must use Java " + minecraftVersion.javaVersion() + ", not " + javaVersion);
        }
        if (loaderVersion.isBlank()) {
            throw new IllegalArgumentException(id + " must declare an exact loader version");
        }
        rejectDynamicVersion(id, "loader", loaderVersion);
        if (!platformApiVersion.isBlank()) {
            rejectDynamicVersion(id, "platform API", platformApiVersion);
        }
        validateAbiCombination(
                id, minecraftVersion, loader, loaderAbi, minecraftAbi, mappingAbi, recipeAbi, networkAbi, menuAbi);
    }

    private static void rejectDynamicVersion(String id, String field, String version) {
        String normalized = version.toLowerCase(java.util.Locale.ROOT);
        if (version.contains("+") && version.endsWith("+")) {
            throw new IllegalArgumentException(id + " uses a dynamic " + field + " version: " + version);
        }
        if (version.contains("*") || version.startsWith("[") || version.startsWith("(")
                || normalized.equals("latest.release") || normalized.equals("latest.integration")
                || normalized.endsWith("-snapshot")) {
            throw new IllegalArgumentException(id + " uses a dynamic " + field + " version: " + version);
        }
    }

    private static void validateAbiCombination(
            String id,
            MinecraftVersion minecraftVersion,
            ModLoader loader,
            LoaderAbi loaderAbi,
            MinecraftAbi minecraftAbi,
            MappingAbi mappingAbi,
            RecipeAbi recipeAbi,
            NetworkAbi networkAbi,
            MenuAbi menuAbi) {
        if (loader == ModLoader.FORGE && minecraftVersion != MinecraftVersion.V1_20_1) {
            throw new IllegalArgumentException(id + " is not a supported Forge ABI coordinate");
        }

        LoaderAbi expectedLoader = loader == ModLoader.FABRIC
                ? LoaderAbi.FABRIC
                : minecraftVersion == MinecraftVersion.V1_20_1
                        ? LoaderAbi.LEGACY_FML : LoaderAbi.MODERN_NEOFORGE;
        MinecraftAbi expectedMinecraft = switch (minecraftVersion) {
            case V1_20_1 -> MinecraftAbi.V1_20_1;
            case V1_21_1 -> MinecraftAbi.V1_21_1;
            case V1_21_4 -> MinecraftAbi.V1_21_4;
            case V26_2 -> MinecraftAbi.V26_2;
        };
        MappingAbi expectedMapping = minecraftVersion == MinecraftVersion.V26_2
                ? MappingAbi.OFFICIAL_UNOBFUSCATED : MappingAbi.MOJANG_REMAPPED;
        RecipeAbi expectedRecipe = switch (minecraftVersion) {
            case V1_20_1 -> RecipeAbi.LEGACY_METHODS;
            case V1_21_1 -> RecipeAbi.CODEC_1_21_1;
            case V1_21_4 -> RecipeAbi.CODEC_WITH_PLACEMENT_1_21_4;
            case V26_2 -> RecipeAbi.SERIALIZER_RECORD_26;
        };
        MenuAbi expectedMenu = switch (minecraftVersion) {
            case V1_20_1 -> MenuAbi.V1_20_1;
            case V1_21_1 -> MenuAbi.V1_21_1;
            case V1_21_4 -> MenuAbi.V1_21_4;
            case V26_2 -> MenuAbi.V26_2;
        };
        NetworkAbi expectedNetwork = expectedNetwork(minecraftVersion, loader);

        requireAbi(id, "loader", expectedLoader, loaderAbi);
        requireAbi(id, "Minecraft", expectedMinecraft, minecraftAbi);
        requireAbi(id, "mapping", expectedMapping, mappingAbi);
        requireAbi(id, "recipe", expectedRecipe, recipeAbi);
        requireAbi(id, "network", expectedNetwork, networkAbi);
        requireAbi(id, "menu", expectedMenu, menuAbi);
    }

    private static NetworkAbi expectedNetwork(MinecraftVersion version, ModLoader loader) {
        if (version == MinecraftVersion.V1_20_1) {
            return loader == ModLoader.FABRIC
                    ? NetworkAbi.FABRIC_LEGACY_CHANNEL : NetworkAbi.FORGE_SIMPLE_CHANNEL;
        }
        if (version == MinecraftVersion.V26_2) {
            return loader == ModLoader.FABRIC
                    ? NetworkAbi.FABRIC_DIRECTIONAL_PAYLOAD : NetworkAbi.NEOFORGE_DIRECTIONAL_PAYLOAD;
        }
        return loader == ModLoader.FABRIC
                ? NetworkAbi.FABRIC_TYPED_PAYLOAD : NetworkAbi.NEOFORGE_TYPED_PAYLOAD;
    }

    private static void requireAbi(String id, String name, Enum<?> expected, Enum<?> actual) {
        if (actual != expected) {
            throw new IllegalArgumentException(
                    id + " requires " + name + " ABI " + expected.name() + ", not " + actual.name());
        }
    }
}
