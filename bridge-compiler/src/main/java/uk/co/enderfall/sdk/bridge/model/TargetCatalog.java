package uk.co.enderfall.sdk.bridge.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Immutable, deterministically ordered collection of bridge targets. */
public final class TargetCatalog {
    private static final List<String> STANDARD_IDS = List.of(
            "1.20.1-fabric",
            "1.20.1-forge",
            "1.20.1-neoforge",
            "1.21.1-fabric",
            "1.21.1-neoforge",
            "1.21.4-fabric",
            "1.21.4-neoforge",
            "26.2-fabric",
            "26.2-neoforge");

    private static final TargetCatalog STANDARD = createStandard();

    private final List<TargetSpec> targets;
    private final Map<String, TargetSpec> byId;

    private TargetCatalog(Collection<TargetSpec> targetSpecs, boolean requireStandardMatrix) {
        Objects.requireNonNull(targetSpecs, "targetSpecs");
        List<TargetSpec> sorted = new ArrayList<>(targetSpecs);
        if (sorted.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Target catalog cannot contain null entries");
        }
        sorted.sort(Comparator.comparing(TargetSpec::id));

        Map<String, TargetSpec> indexed = new LinkedHashMap<>();
        Set<String> duplicateIds = new TreeSet<>();
        Set<String> coordinates = new TreeSet<>();
        Set<String> duplicateCoordinates = new TreeSet<>();
        for (TargetSpec target : sorted) {
            if (indexed.putIfAbsent(target.id(), target) != null) {
                duplicateIds.add(target.id());
            }
            String coordinate = target.minecraftVersion().id() + '/' + target.loader().id();
            if (!coordinates.add(coordinate)) {
                duplicateCoordinates.add(coordinate);
            }
        }

        List<String> violations = new ArrayList<>();
        if (!duplicateIds.isEmpty()) {
            violations.add("duplicate target IDs: " + String.join(", ", duplicateIds));
        }
        if (!duplicateCoordinates.isEmpty()) {
            violations.add("duplicate target coordinates: " + String.join(", ", duplicateCoordinates));
        }
        if (requireStandardMatrix) {
            List<String> actualIds = sorted.stream().map(TargetSpec::id).toList();
            if (!actualIds.equals(STANDARD_IDS)) {
                violations.add("standard matrix must be exactly: " + String.join(", ", STANDARD_IDS));
            }
        }
        if (!violations.isEmpty()) {
            violations.sort(String::compareTo);
            throw new IllegalArgumentException("Invalid target catalog: " + String.join("; ", violations));
        }

        targets = List.copyOf(sorted);
        byId = Map.copyOf(indexed);
    }

    /** Returns the reviewed nine-target EnderFall catalog. */
    public static TargetCatalog standard() {
        return STANDARD;
    }

    /** Creates a validated custom catalog, primarily for compiler tests and future extensions. */
    public static TargetCatalog of(Collection<TargetSpec> targets) {
        return new TargetCatalog(targets, false);
    }

    /** Returns targets in stable target-ID order. */
    public List<TargetSpec> targets() {
        return targets;
    }

    /** Returns stable target IDs in the same order as {@link #targets()}. */
    public List<String> targetIds() {
        return targets.stream().map(TargetSpec::id).toList();
    }

    /** Finds a target by its exact stable ID. */
    public Optional<TargetSpec> find(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(byId.get(id));
    }

    /** Resolves an exact target ID or throws an error that lists all known targets. */
    public TargetSpec require(String id) {
        Objects.requireNonNull(id, "id");
        if (!id.matches("[0-9]+(?:\\.[0-9]+){1,2}-(?:fabric|forge|neoforge)")) {
            throw new IllegalArgumentException(
                    "Unsafe or malformed target ID '" + id + "'. Known targets: " + String.join(", ", targetIds()));
        }
        TargetSpec target = byId.get(id);
        if (target == null) {
            throw new IllegalArgumentException(
                    "Unknown target '" + id + "'. Known targets: " + String.join(", ", targetIds()));
        }
        return target;
    }

    private static TargetCatalog createStandard() {
        return new TargetCatalog(List.of(
                target(MinecraftVersion.V1_20_1, ModLoader.FABRIC, "0.19.5", "0.92.12+1.20.1",
                        LoaderAbi.FABRIC, MinecraftAbi.V1_20_1, MappingAbi.MOJANG_REMAPPED,
                        RecipeAbi.LEGACY_METHODS, NetworkAbi.FABRIC_LEGACY_CHANNEL, MenuAbi.V1_20_1),
                target(MinecraftVersion.V1_20_1, ModLoader.FORGE, "47.4.23", "",
                        LoaderAbi.LEGACY_FML, MinecraftAbi.V1_20_1, MappingAbi.MOJANG_REMAPPED,
                        RecipeAbi.LEGACY_METHODS, NetworkAbi.FORGE_SIMPLE_CHANNEL, MenuAbi.V1_20_1),
                target(MinecraftVersion.V1_20_1, ModLoader.NEOFORGE, "47.1.106", "",
                        LoaderAbi.LEGACY_FML, MinecraftAbi.V1_20_1, MappingAbi.MOJANG_REMAPPED,
                        RecipeAbi.LEGACY_METHODS, NetworkAbi.FORGE_SIMPLE_CHANNEL, MenuAbi.V1_20_1),
                target(MinecraftVersion.V1_21_1, ModLoader.FABRIC, "0.19.5", "0.116.17+1.21.1",
                        LoaderAbi.FABRIC, MinecraftAbi.V1_21_1, MappingAbi.MOJANG_REMAPPED,
                        RecipeAbi.CODEC_1_21_1, NetworkAbi.FABRIC_TYPED_PAYLOAD, MenuAbi.V1_21_1),
                target(MinecraftVersion.V1_21_1, ModLoader.NEOFORGE, "21.1.249", "",
                        LoaderAbi.MODERN_NEOFORGE, MinecraftAbi.V1_21_1, MappingAbi.MOJANG_REMAPPED,
                        RecipeAbi.CODEC_1_21_1, NetworkAbi.NEOFORGE_TYPED_PAYLOAD, MenuAbi.V1_21_1),
                target(MinecraftVersion.V1_21_4, ModLoader.FABRIC, "0.19.5", "0.119.4+1.21.4",
                        LoaderAbi.FABRIC, MinecraftAbi.V1_21_4, MappingAbi.MOJANG_REMAPPED,
                        RecipeAbi.CODEC_WITH_PLACEMENT_1_21_4, NetworkAbi.FABRIC_TYPED_PAYLOAD, MenuAbi.V1_21_4),
                target(MinecraftVersion.V1_21_4, ModLoader.NEOFORGE, "21.4.157", "",
                        LoaderAbi.MODERN_NEOFORGE, MinecraftAbi.V1_21_4, MappingAbi.MOJANG_REMAPPED,
                        RecipeAbi.CODEC_WITH_PLACEMENT_1_21_4, NetworkAbi.NEOFORGE_TYPED_PAYLOAD, MenuAbi.V1_21_4),
                target(MinecraftVersion.V26_2, ModLoader.FABRIC, "0.19.5", "0.159.0+26.2",
                        LoaderAbi.FABRIC, MinecraftAbi.V26_2, MappingAbi.OFFICIAL_UNOBFUSCATED,
                        RecipeAbi.SERIALIZER_RECORD_26, NetworkAbi.FABRIC_DIRECTIONAL_PAYLOAD, MenuAbi.V26_2),
                target(MinecraftVersion.V26_2, ModLoader.NEOFORGE, "26.2.0.75", "",
                        LoaderAbi.MODERN_NEOFORGE, MinecraftAbi.V26_2, MappingAbi.OFFICIAL_UNOBFUSCATED,
                        RecipeAbi.SERIALIZER_RECORD_26, NetworkAbi.NEOFORGE_DIRECTIONAL_PAYLOAD, MenuAbi.V26_2)), true);
    }

    private static TargetSpec target(
            MinecraftVersion minecraftVersion,
            ModLoader loader,
            String loaderVersion,
            String platformApiVersion,
            LoaderAbi loaderAbi,
            MinecraftAbi minecraftAbi,
            MappingAbi mappingAbi,
            RecipeAbi recipeAbi,
            NetworkAbi networkAbi,
            MenuAbi menuAbi) {
        return new TargetSpec(
                minecraftVersion.id() + '-' + loader.id(),
                minecraftVersion,
                loader,
                minecraftVersion.javaVersion(),
                loaderVersion,
                platformApiVersion,
                loaderAbi,
                minecraftAbi,
                mappingAbi,
                recipeAbi,
                networkAbi,
                menuAbi);
    }
}
