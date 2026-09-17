package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;
import static uk.co.enderfall.sdk.bridge.PlatformOperation.*;

/**
 * Assembles platform services from shared operations and reviewed native ABI facets.
 * Canonical paths select features; no canonical Java text or reference folder is read.
 */
final class PlatformServiceEmitter {
    private PlatformServiceEmitter() { }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> declarations) throws BridgeGenerationException {
        return emit(target, declarations, false);
    }

    static RuntimeSource persistentPreview() throws BridgeGenerationException {
        return persistentPreview("1.21.4-fabric");
    }

    static RuntimeSource persistentPreview(String target) throws BridgeGenerationException {
        boolean fabric = BlockEntityNativePolicy.require(target).fabric();
        String root = "uk/co/enderfall/sdk/runtime/" + (fabric ? "fabric/v1_21_4/Fabric" : "neoforge/v1_21_4/NeoForge");
        Set<String> declarations = new java.util.HashSet<>();
        for (String suffix : List.of("PlatformAdapter", "PlatformInfo", "ClientHooks", "CommandBridge",
                "RecipeBinding", "WorkbenchBinding", "WorkbenchMenu", "WorkbenchRecipe", "RawPayload")) {
            declarations.add(root + suffix + ".java");
        }
        return emit(uk.co.enderfall.sdk.bridge.model.TargetCatalog.standard().require(target),
                declarations, true).get(0);
    }

    private static List<RuntimeSource> emit(TargetSpec target, Set<String> declarations, boolean persistent)
            throws BridgeGenerationException {
        if (persistent) BlockEntityNativePolicy.require(target.id());
        NativePlatformPolicy policy = NativePlatformPolicy.forTarget(target);
        String canonicalPrefix = policy.fabric() ? "Fabric" : "NeoForge";
        String inputPackage = "uk/co/enderfall/sdk/runtime/" + (policy.fabric() ? "fabric" : "neoforge") + "/v1_21_4/";
        String canonical = inputPackage + canonicalPrefix + "PlatformAdapter.java";
        if (!declarations.contains(canonical)) return List.of();
        for (String suffix : List.of("PlatformInfo", "ClientHooks", "CommandBridge", "RecipeBinding",
                "WorkbenchBinding", "WorkbenchMenu", "WorkbenchRecipe")) {
            String required = inputPackage + canonicalPrefix + suffix + ".java";
            if (!declarations.contains(required)) throw new BridgeGenerationException(target.id() + " requires platform declaration " + required);
        }
        if (!policy.legacyChannel() && !declarations.contains(inputPackage + canonicalPrefix + "RawPayload.java")) {
            throw new BridgeGenerationException(target.id() + " requires platform raw-payload declaration");
        }
        String prefix = policy == NativePlatformPolicy.LEGACY_FML ? "LegacyForge" : canonicalPrefix;
        String workbenchPrefix = policy == NativePlatformPolicy.FABRIC_LEGACY ? "Fabric1201"
                : policy == NativePlatformPolicy.NEOFORGE_IDENTIFIER ? "NeoForge26" : prefix;
        String packageName = "uk.co.enderfall.sdk.runtime." + (policy == NativePlatformPolicy.LEGACY_FML
                ? "forge.v1_20_1" : policy.fabric() ? "fabric.v1_21_4" : "neoforge.v1_21_4");
        Map<String, String> names = Map.ofEntries(
                Map.entry("PACKAGE", packageName),
                Map.entry("PlatformAdapter", prefix + (persistent ? "PersistentPlatformAdapter" : "PlatformAdapter")),
                Map.entry("PlatformInfo", prefix + "PlatformInfo"),
                Map.entry("CommandBridge", prefix + "CommandBridge"),
                Map.entry("ClientHooks", prefix + "ClientHooks"),
                Map.entry("RawPayload", prefix + "RawPayload"),
                Map.entry("RecipeBinding", workbenchPrefix + "RecipeBinding"),
                Map.entry("WorkbenchBinding", workbenchPrefix + "WorkbenchBinding"),
                Map.entry("WorkbenchRecipe", workbenchPrefix + "WorkbenchRecipe"),
                Map.entry("WorkbenchMenu", workbenchPrefix + "WorkbenchMenu"));
        StringBuilder source = new StringBuilder();
        for (PlatformOperation operation : operations(policy)) {
            source.append(persistent && (operation == PlatformOperation.REGISTER_BLOCK || operation == PlatformOperation.BLOCK_PROPERTIES)
                    ? PlatformRegistrationSources.emitWithPropertyCopy(operation, policy) : fragment(operation, policy));
        }
        if (persistent) source.append(BlockPropertyCopySources.operations(BlockEntityNativePolicy.require(target.id())));
        if (persistent) source.append(PersistentPlatformSources.operations(BlockEntityNativePolicy.require(target.id())));
        source.append("}\n");
        String rendered = renderNames(source.toString(), names);
        if (persistent) rendered = rendered.replace("Capability.CUSTOM_RECIPES, Capability.CONTAINER_MENUS",
                "Capability.CUSTOM_RECIPES, Capability.CONTAINER_MENUS, Capability.BLOCK_STATES");
        if (persistent && BlockEntityNativePolicy.require(target.id()).unobfuscated()) rendered = BlockEntity26Sources.names(rendered);
        String filePrefix = switch (policy) {
            case FABRIC_LEGACY -> "Fabric1201";
            case FABRIC_UNKEYED -> "Fabric1211";
            case FABRIC_IDENTIFIER -> "Fabric26";
            case NEOFORGE_IDENTIFIER -> "NeoForge26";
            default -> prefix;
        };
        return List.of(new RuntimeSource(canonical, packageName.replace('.', '/') + "/" + filePrefix
                + (persistent ? "PersistentPlatformAdapter.java" : "PlatformAdapter.java"),
                rendered.getBytes(StandardCharsets.UTF_8)));
    }

    static String renderNames(String template, Map<String, String> names) throws BridgeGenerationException {
        String rendered = template;
        for (var entry : names.entrySet()) rendered = rendered.replace("${" + entry.getKey() + "}", entry.getValue());
        if (rendered.contains("${")) throw new BridgeGenerationException("Unresolved native platform template name");
        return rendered;
    }

    static List<PlatformOperation> operations(NativePlatformPolicy policy) {
        List<PlatformOperation> operations = new ArrayList<>(List.of(
                HEADER, CONSTRUCTOR, ATTACH, PLATFORM_INFO, CAPABILITIES, COMMON_CONFIG_DIRECTORY, SERVER_CONFIG_DIRECTORY,
                REGISTER_ITEM, REGISTER_BLOCK, REGISTER_CREATIVE_TAB, REGISTER_COMMAND, REGISTER_WORKBENCH_RECIPE_TYPE,
                REGISTER_WORKBENCH, OPEN_WORKBENCH, REGISTER_PAYLOAD, SEND_TO_SERVER, SEND_TO_PLAYER, SEND_TO_ALL,
                CONNECTED_PLAYERS, PLAYER_SNAPSHOT, COUNT_PLAYER_ITEM, CONSUME_PLAYER_ITEMS, GIVE_PLAYER_ITEM,
                SEND_PLAYER_MESSAGE, HEAL_PLAYER, ADD_PLAYER_EXPERIENCE, SHOW_MENU, UPDATE_MENU, CLOSE_MENU));
        if (!policy.fabric()) {
            if (policy != NativePlatformPolicy.LEGACY_FML) operations.add(REGISTER_PAYLOAD_HANDLERS);
            operations.add(RECEIVE);
        }
        operations.add(INSTALL_EVENTS);
        if (!policy.fabric()) operations.add(PUBLISH_PLAYER);
        operations.addAll(List.of(INTERACTION, PUBLISH_LIFECYCLE, REQUIRE_ITEM, REQUIRE_PAYLOAD,
                REQUIRE_SERVER, ONLINE_PLAYER, REQUIRE_ONLINE_PLAYER));
        if (policy == NativePlatformPolicy.FABRIC_LEGACY) operations.add(READ_PAYLOAD);
        operations.add(policy.identifier() ? IDENTIFIER : LOCATION);
        if (policy == NativePlatformPolicy.NEOFORGE_IDENTIFIER) operations.add(REQUIRE_BINDING);
        else if (policy != NativePlatformPolicy.FABRIC_LEGACY) operations.addAll(List.of(REQUIRE_RECIPE_BINDING, REQUIRE_WORKBENCH_BINDING));
        operations.add(ITEM_PROPERTIES);
        if (!policy.fabric()) operations.add(BLOCK_PROPERTIES);
        operations.addAll(List.of(SOUND, PAYLOAD_BINDING));
        return List.copyOf(operations);
    }

    private static String fragment(PlatformOperation operation, NativePlatformPolicy policy) throws BridgeGenerationException {
        return switch (operation) {
            case HEADER, CONSTRUCTOR, ATTACH, PLATFORM_INFO, CAPABILITIES, COMMON_CONFIG_DIRECTORY, SERVER_CONFIG_DIRECTORY -> PlatformStateSources.emit(operation, policy);
            case REGISTER_ITEM, REGISTER_BLOCK, REGISTER_CREATIVE_TAB, REGISTER_COMMAND, REGISTER_WORKBENCH_RECIPE_TYPE, REGISTER_WORKBENCH, OPEN_WORKBENCH, REQUIRE_RECIPE_BINDING, REQUIRE_WORKBENCH_BINDING, REQUIRE_BINDING, ITEM_PROPERTIES, BLOCK_PROPERTIES, SOUND -> PlatformRegistrationSources.emit(operation, policy);
            case REGISTER_PAYLOAD, SEND_TO_SERVER, SEND_TO_PLAYER, SEND_TO_ALL, REGISTER_PAYLOAD_HANDLERS, RECEIVE, READ_PAYLOAD, REQUIRE_PAYLOAD, PAYLOAD_BINDING -> PlatformNetworkingSources.emit(operation, policy);
            case CONNECTED_PLAYERS, PLAYER_SNAPSHOT, COUNT_PLAYER_ITEM, CONSUME_PLAYER_ITEMS, GIVE_PLAYER_ITEM, SEND_PLAYER_MESSAGE, HEAL_PLAYER, ADD_PLAYER_EXPERIENCE, SHOW_MENU, UPDATE_MENU, CLOSE_MENU, REQUIRE_ITEM, REQUIRE_SERVER, ONLINE_PLAYER, REQUIRE_ONLINE_PLAYER, LOCATION, IDENTIFIER -> PlatformGameplaySources.emit(operation, policy);
            case INSTALL_EVENTS, INTERACTION, PUBLISH_LIFECYCLE, PUBLISH_PLAYER -> PlatformEventSources.emit(operation, policy);
        };
    }
}
