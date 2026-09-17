package uk.co.enderfall.sdk.bridge;

/** Reviewed native storage ABI choices, separate from complete platform/menu support. */
enum BlockEntityNativePolicy {
    FABRIC_1211("1.21.1-fabric", "properties"),
    FABRIC_1214("1.21.4-fabric", "properties.setId(ResourceKey.create(Registries.BLOCK, id))"),
    NEOFORGE_1211("1.21.1-neoforge", "properties"),
    NEOFORGE_1214("1.21.4-neoforge", "properties.setId(ResourceKey.create(Registries.BLOCK, id))"),
    FABRIC_1201("1.20.1-fabric", "properties"),
    FORGE_1201("1.20.1-forge", "properties"),
    NEOFORGE_1201("1.20.1-neoforge", "properties"),
    FABRIC_26("26.2-fabric", "properties.setId(ResourceKey.create(Registries.BLOCK, id))"),
    NEOFORGE_26("26.2-neoforge", "properties.setId(ResourceKey.create(Registries.BLOCK, id))");

    private final String target;
    private final String blockProperties;
    BlockEntityNativePolicy(String target, String blockProperties) {
        this.target = target;
        this.blockProperties = blockProperties;
    }
    String target() { return target; }
    boolean fabric() { return target.endsWith("-fabric"); }
    boolean legacy() { return target.startsWith("1.20.1-"); }
    boolean unobfuscated() { return target.startsWith("26."); }
    String prefix() { return legacy() ? (fabric() ? "Fabric1201" : "LegacyForge") : (fabric() ? "Fabric" : unobfuscated() ? "NeoForge26" : "NeoForge"); }
    String runtimePackage() { return legacy() && !fabric() ? "forge.v1_20_1" : (fabric() ? "fabric.v1_21_4" : "neoforge.v1_21_4"); }
    String blockProperties() { return blockProperties; }
    String builderImport() {
        return this == FABRIC_1214 ? "import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;" : "";
    }
    String typeFactory() { return this == FABRIC_1214 ? "FabricBlockEntityTypeBuilder.create" : "BlockEntityType.Builder.of"; }
    String typeBuildArgument() { return this == FABRIC_1214 ? "" : "null"; }
    String nativeId() { return legacy() ? "Objects.requireNonNull(ResourceLocation.tryParse(spec.block().id().toString()))" : "ResourceLocation.parse(spec.block().id().toString())"; }
    boolean modernRecipes() { return target.startsWith("1.21.4-") || unobfuscated(); }
    String itemProperties() { return this == FABRIC_1214 || this == FABRIC_26 ? "itemProperties(itemSpec).setId(itemKey).useBlockDescriptionPrefix()" : "itemProperties(itemSpec)"; }
    String itemKeyDeclaration() { return this == FABRIC_1214 || this == FABRIC_26 ? "ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, location(id));" : "var itemKey = location(id);"; }

    static BlockEntityNativePolicy require(String target) {
        for (var policy : values()) if (policy.target.equals(target)) return policy;
        throw new IllegalArgumentException("Unreviewed block-entity storage target: " + target);
    }
}
