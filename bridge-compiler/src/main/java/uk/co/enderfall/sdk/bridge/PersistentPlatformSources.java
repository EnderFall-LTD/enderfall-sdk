package uk.co.enderfall.sdk.bridge;

/** Additional operations composed with the normal platform services in the isolated preview. */
final class PersistentPlatformSources {
    private PersistentPlatformSources() { }

    static String operations() {
        return operations(BlockEntityNativePolicy.FABRIC_1214);
    }

    static String operations(BlockEntityNativePolicy policy) {
        return """

                    private final Map<ResourceId, uk.co.enderfall.sdk.runtime.blockentity.nativebridge.StoredBlockEntity.Binding>
                            persistentBlocks = new LinkedHashMap<>();
                    private final java.util.Set<ResourceId> boundPersistentBlocks = new java.util.HashSet<>();
                    private final Map<ResourceId, ${PREFIX}TimedWorkbenchMenu.Binding> timedWorkbenches = new LinkedHashMap<>();
                    @Override public boolean supportsTimedWorkbenches() { return true; }
                    private boolean menuGauges;
                    public void enableMenuGauges() { menuGauges = true; }
                    @Override public boolean supportsMenuGauges() { return menuGauges; }
                    ${ITEM_RENDERER_REGISTRATION}

                    @Override
                    public void bindTankMenu(uk.co.enderfall.sdk.api.registry.BlockRef block,
                            uk.co.enderfall.sdk.api.fluid.FluidTankSpec tank,
                            java.util.function.BiConsumer<java.util.UUID, uk.co.enderfall.sdk.api.ui.MenuStateSource> opener) {
                        var binding = persistentBlocks.get(block.id());
                        if (binding == null || !tank.equals(binding.definition().tanks().get(tank.name()))
                                || boundPersistentBlocks.contains(block.id())) {
                            throw new IllegalArgumentException("Unknown tank or already bound persistent block: " + block.id());
                        }
                        binding.onUse((player, event) -> {
                            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                                var level = serverPlayer.level();
                                var hit = event.blockLocation().orElseThrow();
                                var pos = new net.minecraft.core.BlockPos(hit.x(), hit.y(), hit.z());
                                if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return;
                                var original = level.getBlockEntity(pos);
                                if (!(original instanceof uk.co.enderfall.sdk.runtime.blockentity.nativebridge.StoredBlockEntity owner)
                                        || original.getType() != binding.type()) return;
                                opener.accept(player.getUUID(), () -> {
                                    if (serverPlayer.level() != level || !serverPlayer.isAlive() || serverPlayer.isSpectator()
                                            || serverPlayer.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0
                                            || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                                            || owner.isRemoved() || level.getBlockEntity(pos) != owner) {
                                        return java.util.Optional.empty();
                                    }
                                    return java.util.Optional.of(uk.co.enderfall.sdk.api.ui.MenuState.builder()
                                            .tank("tank", owner.fluidTank(tank)).build());
                                });
                            }
                            event.handle();
                        });
                        boundPersistentBlocks.add(block.id());
                    }

                    @Override
                    public boolean supportsPersistentWorkbenches() { return true; }

                    @Override
                    public void registerPersistentBlock(ResourceId id, BlockSpec spec, ItemSpec itemSpec,
                            uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec storage) {
                        if (!id.namespace().equals(modId) || !id.equals(storage.block().id())) {
                            throw new IllegalArgumentException("[" + modId + "] Persistent block ownership mismatch: " + id);
                        }
                        if (blocks.containsKey(id) || items.containsKey(id) || persistentBlocks.containsKey(id)
                                || ${ITEM_REGISTRY}.containsKey(location(id))) {
                            throw new IllegalStateException("[" + modId + "] Duplicate persistent block/item: " + id);
                        }
                        ${BLOCK_REGISTRATION}
                        persistentBlocks.put(id, binding);
                    }

                    @Override
                    public void registerPersistentWorkbench(PortableWorkbenchDefinition definition) {
                        var storage = definition.spec().storage().orElseThrow(
                                () -> new IllegalArgumentException("Persistent workbench needs storage"));
                        ResourceId blockId = storage.block().id();
                        var owner = persistentBlocks.get(blockId);
                        if (owner == null || storage.inventorySlots() != owner.definition().inventorySlots()
                                || !storage.fields().equals(owner.definition().fields())
                                || !storage.tanks().equals(owner.definition().tanks())
                                || boundPersistentBlocks.contains(blockId)) {
                            throw new IllegalArgumentException("[" + modId + "] Unknown, mismatched, or already bound persistent block: " + blockId);
                        }
                        if (definition.spec().processingTicks() > 0) {
                            ResourceId id = definition.reference().id();
                            var recipes = recipeTypes.get(definition.spec().recipeType().id());
                            if (recipes == null || timedWorkbenches.containsKey(id) || workbenches.containsKey(id)
                                    || ${MENU_REGISTRY}.containsKey(location(id))) {
                                throw new IllegalArgumentException("[" + modId + "] Missing recipes or duplicate timed menu: " + id);
                            }
                            java.util.concurrent.atomic.AtomicReference<${PREFIX}TimedWorkbenchMenu.Binding> reference = new java.util.concurrent.atomic.AtomicReference<>();
                            ${TIMED_REGISTRATION}
                            reference.set(timed);
                            if (definition.spec().machineRecipes()) ${PREFIX}TimedWorkbenchProcessor.bindMachine(owner, recipes, definition.spec());
                            else ${PREFIX}TimedWorkbenchProcessor.bind(owner, recipes, definition.spec().processingTicks());
                            owner.onUse((player, event) -> {
                                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                                    ${PREFIX}TimedWorkbenchMenu.openAt(serverPlayer, timed, event.blockLocation().orElseThrow());
                                }
                                event.handle();
                            });
                            timedWorkbenches.put(id, timed);
                            boundPersistentBlocks.add(blockId);
                            if (platformInfo.environment() == Environment.CLIENT) ${PREFIX}TimedWorkbenchClient.register(${CLIENT_ARGS});
                            return;
                        }
                        // Reuse the normal client menu type, recipe binding, and screen registration.
                        registerWorkbench(definition);
                        ${PREFIX}PersistentWorkbenchMenu.bindBlock(owner, workbenches.get(definition.reference().id()));
                        boundPersistentBlocks.add(blockId);
                    }

                    @Override
                    public void openPersistentWorkbench(java.util.UUID playerId, PortableWorkbenchDefinition definition,
                            uk.co.enderfall.sdk.api.blockentity.BlockLocation location) {
                        if (definition.spec().processingTicks() > 0) {
                            var timed = timedWorkbenches.get(definition.reference().id());
                            if (timed == null || timed.definition() != definition) throw new IllegalArgumentException("Unknown timed workbench");
                            ${PREFIX}TimedWorkbenchMenu.openAt(requireOnlinePlayer(playerId), timed, location);
                            return;
                        }
                        var binding = workbenches.get(definition.reference().id());
                        if (binding == null || binding.definition() != definition || definition.spec().storage().isEmpty()) {
                            throw new IllegalArgumentException("[" + modId + "] Unknown persistent workbench: " + definition.reference().id());
                        }
                        ${PREFIX}PersistentWorkbenchMenu.openAt(requireOnlinePlayer(playerId), binding, location);
                    }
                """.replace("${BLOCK_REGISTRATION}", blockRegistration(policy))
                .replace("${ITEM_RENDERER_REGISTRATION}", BlockEntityItemRendererSources.registration(policy))
                .replace("${ITEM_REGISTRY}", policy.legacy() && !policy.fabric() ? "net.minecraftforge.registries.ForgeRegistries.ITEMS" : "net.minecraft.core.registries.BuiltInRegistries.ITEM")
                .replace("${MENU_REGISTRY}", policy.legacy() && !policy.fabric() ? "net.minecraftforge.registries.ForgeRegistries.MENU_TYPES" : "net.minecraft.core.registries.BuiltInRegistries.MENU")
                .replace("${TIMED_REGISTRATION}", timedRegistration(policy))
                .replace("${CLIENT_ARGS}", policy.fabric() ? "type" : "modBus, type")
                .replace("${PERSISTENT_ITEM_PROPERTIES}", policy.itemProperties())
                .replace("${PERSISTENT_ITEM_KEY}", policy.itemKeyDeclaration())
                .replace("${PREFIX}", policy.prefix());
    }


    private static String blockRegistration(BlockEntityNativePolicy policy) {
        if (policy.fabric()) return """
                        BlockBehaviour.Properties properties = copiedBlockProperties(spec);
                        ${PERSISTENT_ITEM_KEY}
                        Item.Properties itemProperties = ${PERSISTENT_ITEM_PROPERTIES};
                        var binding = uk.co.enderfall.sdk.runtime.blockentity.nativebridge.StoredBlockEntity.register(storage, properties, spec);
                        Block block = binding.block();
                        Item item = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, itemProperties));
                        blocks.put(id, block);
                        items.put(id, item);

                """;
        return """
                        var binding = uk.co.enderfall.sdk.runtime.blockentity.nativebridge.StoredBlockEntity.register(
                                storage, blockProperties(spec), spec, modBus);
                        java.util.function.Supplier<Block> block = binding::block;
                        blocks.put(id, ${BLOCK_HANDLE});
                        var item = ${REGISTER_ITEM};
                        items.put(id, item);
                """.replace("${BLOCK_HANDLE}", policy.legacy() ? "binding.blockHandle()" : "block")
                .replace("${REGISTER_ITEM}", policy.legacy()
                        ? "itemRegister.register(id.path(), () -> new net.minecraft.world.item.BlockItem(block.get(), itemProperties(itemSpec)))"
                        : policy.unobfuscated() ? "itemRegister.registerSimpleBlockItem(id.path(), block, () -> itemProperties(itemSpec))"
                        : "itemRegister.registerSimpleBlockItem(id.path(), block, itemProperties(itemSpec))");
    }

    private static String timedRegistration(BlockEntityNativePolicy policy) {
        if (policy.fabric()) return """
                            MenuType<${PREFIX}TimedWorkbenchMenu> type = Registry.register(BuiltInRegistries.MENU, location(id),
                                    new MenuType<>((containerId, inventory) -> new ${PREFIX}TimedWorkbenchMenu(
                                            containerId, inventory, java.util.Objects.requireNonNull(reference.get()), null), FeatureFlags.VANILLA_SET));
                            var timed = new ${PREFIX}TimedWorkbenchMenu.Binding(definition, recipes, () -> type);

                """;
        return """
                            java.util.function.Supplier<MenuType<${PREFIX}TimedWorkbenchMenu>> type = menuRegister.register(id.path(),
                                    () -> new MenuType<>((containerId, inventory) -> new ${PREFIX}TimedWorkbenchMenu(
                                            containerId, inventory, java.util.Objects.requireNonNull(reference.get()), null), FeatureFlags.VANILLA_SET));
                            var timed = new ${PREFIX}TimedWorkbenchMenu.Binding(definition, recipes, type);
                """;
    }

    static String bootstrap() { return bootstrap(true); }
    static String bootstrap(boolean fabric) {
        return bootstrap(fabric ? BlockEntityNativePolicy.FABRIC_1214 : BlockEntityNativePolicy.NEOFORGE_1214);
    }
    static String bootstrap(BlockEntityNativePolicy policy) {
        boolean fabric = policy.fabric();
        return """
                package uk.co.enderfall.sdk.runtime.${PACKAGE};

                import uk.co.enderfall.sdk.runtime.RuntimeModBootstrap;
                import uk.co.enderfall.sdk.runtime.RuntimeModContext;

                /** Explicit development-only bootstrap. Never selected by normal generated consumer metadata. */
                public final class ${PREFIX}PersistentPreviewBootstrap {
                    private static final java.util.Set<String> INITIALIZED = java.util.concurrent.ConcurrentHashMap.newKeySet();
                    private ${PREFIX}PersistentPreviewBootstrap() { }

                    public static RuntimeModContext initialize(String modId, String commonEntrypoint, String clientEntrypoint${BOOTSTRAP_ARGS}) {
                        // Failed native registration cannot be rolled back safely; prohibit retry in this process.
                        if (!INITIALIZED.add(modId)) {
                            throw new IllegalStateException("[" + modId + "] Persistent preview initialized twice");
                        }
                        ${PLATFORM_PREFIX}PersistentPlatformAdapter adapter = new ${PLATFORM_PREFIX}PersistentPlatformAdapter(modId${ADAPTER_ARGS});
                        RuntimeModContext context = RuntimeModBootstrap.initialize(modId, commonEntrypoint,
                                clientEntrypoint, adapter, Thread.currentThread().getContextClassLoader());
                        adapter.attach(context);
                        return context;
                    }
                }
                """.replace("${BOOTSTRAP_ARGS}", fabric ? "" : policy.legacy() ? ", net.minecraftforge.eventbus.api.IEventBus modBus" : ", net.neoforged.bus.api.IEventBus modBus")
                .replace("${ADAPTER_ARGS}", fabric ? "" : ", modBus")
                .replace("${PACKAGE}", policy.runtimePackage()).replace("${PREFIX}", policy.prefix())
                .replace("${PLATFORM_PREFIX}", fabric ? "Fabric" : policy.legacy() ? "LegacyForge" : "NeoForge");
    }
}
