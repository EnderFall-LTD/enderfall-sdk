package uk.co.enderfall.sdk.bridge;

/** Native registration lifecycle policies; all inventory and save behaviour stays shared. */
final class BlockEntityRegistrationSources {
    private BlockEntityRegistrationSources() { }
    static String emit(BlockEntityNativePolicy policy) {
        if (policy.fabric()) return """
                    /** Register during Fabric common initialization, before registries freeze. No block item is created. */
                    public static Binding register(BlockEntitySpec spec, BlockBehaviour.Properties properties) {
                        return register(spec, properties, uk.co.enderfall.sdk.api.registry.BlockSpec.builder().build());
                    }
                    public static Binding register(BlockEntitySpec spec, BlockBehaviour.Properties properties,
                            uk.co.enderfall.sdk.api.registry.BlockSpec blockSpec) {
                        Objects.requireNonNull(spec, "spec");
                        Objects.requireNonNull(properties, "properties");
                        ResourceLocation id = ${NATIVE_ID};
                        if (BuiltInRegistries.BLOCK.containsKey(id) || BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id)) {
                            throw new IllegalArgumentException("Duplicate persistent block/type: " + id);
                        }
                        Binding binding = new Binding(spec, blockSpec);
                        binding.block = Registry.register(BuiltInRegistries.BLOCK, id,
                                createStoredBlock(${BLOCK_PROPERTIES}, binding));
                        binding.type = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id,
                                ${TYPE_FACTORY}(
                                        (pos, state) -> new StoredBlockEntity(binding, pos, state), binding.block).build(${TYPE_BUILD_ARGUMENT}));
                        return binding;
                    }
                """;
        return """
                    /** Deferred registration on the owning mod bus; no block item is created here. */
                    public static Binding register(BlockEntitySpec spec, BlockBehaviour.Properties properties,
                            ${BUS} modBus) {
                        return register(spec, properties, uk.co.enderfall.sdk.api.registry.BlockSpec.builder().build(), modBus);
                    }
                    public static Binding register(BlockEntitySpec spec, BlockBehaviour.Properties properties,
                            uk.co.enderfall.sdk.api.registry.BlockSpec blockSpec, ${BUS} modBus) {
                        Objects.requireNonNull(spec, "spec");
                        Objects.requireNonNull(properties, "properties");
                        Objects.requireNonNull(modBus, "modBus");
                        ResourceLocation id = ${NATIVE_ID};
                        Binding binding = new Binding(spec, blockSpec);
                        var blocks = ${DEFERRED_REGISTER}.create(Registries.BLOCK, id.getNamespace());
                        var types = ${DEFERRED_REGISTER}.create(Registries.BLOCK_ENTITY_TYPE, id.getNamespace());
                        var block = blocks.register(id.getPath(), () -> {
                            binding.block = createStoredBlock(${BLOCK_PROPERTIES}, binding);
                            return binding.block;
                        });
                        types.register(id.getPath(), () -> {
                            binding.type = ${DEFERRED_TYPE};
                            return binding.type;
                        });
                        ${CAPTURE_BLOCK_HANDLE}
                        blocks.register(modBus);
                        types.register(modBus);
                        return binding;
                    }
                """.replace("${BUS}", policy.legacy() ? "net.minecraftforge.eventbus.api.IEventBus" : "net.neoforged.bus.api.IEventBus")
                .replace("${CAPTURE_BLOCK_HANDLE}", policy.legacy() ? "binding.blockHandle = block;" : "")
                .replace("${DEFERRED_REGISTER}", policy.legacy() ? "net.minecraftforge.registries.DeferredRegister" : "net.neoforged.neoforge.registries.DeferredRegister")
                .replace("${DEFERRED_TYPE}", !policy.modernRecipes()
                        ? "BlockEntityType.Builder.of((pos, state) -> new StoredBlockEntity(binding, pos, state), block.get()).build(null)"
                        : "new BlockEntityType<>((pos, state) -> new StoredBlockEntity(binding, pos, state), java.util.Set.of(block.get()))");
    }
}
