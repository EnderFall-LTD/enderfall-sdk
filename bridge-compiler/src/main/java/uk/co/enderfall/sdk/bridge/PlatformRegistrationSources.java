package uk.co.enderfall.sdk.bridge;

/**
 * Shared registration operations with explicit native ABI facets.
 * These are authored compiler templates, not contextual edits of canonical Java.
 * Layout intentionally retains reference source and bytecode parity.
 */
final class PlatformRegistrationSources {
    private PlatformRegistrationSources() { }

    static String emitWithPropertyCopy(PlatformOperation operation, NativePlatformPolicy policy) throws BridgeGenerationException {
        if (operation != PlatformOperation.REGISTER_BLOCK && operation != PlatformOperation.BLOCK_PROPERTIES) return emit(operation, policy);
        String source = emit(operation, policy);
        // These are exact expressions owned by the templates below, not edits to reference files.
        for (String key : new String[] { "", "\n                .setId(blockKey)" }) {
            String expression = "BlockBehaviour.Properties.of()" + key
                    + "\n                .strength(spec.hardness(), spec.resistance())"
                    + "\n                .friction(spec.friction())"
                    + "\n                .jumpFactor(spec.jumpFactor())"
                    + "\n                .lightLevel(state -> spec.luminance())"
                    + "\n                .sound(sound(spec))";
            source = source.replace(expression, "copiedBlockProperties(spec)" + (key.isEmpty() ? "" : ".setId(blockKey)"));
        }
        if (source.contains("BlockBehaviour.Properties.of()")) throw new BridgeGenerationException("Unconverted block property template for " + policy);
        return source.replace("if (spec.requiresTool())", "if (spec.overrides(BlockSpec.Property.TOOL) && spec.requiresTool())")
                .replace("new Block(properties)", "uk.co.enderfall.sdk.runtime.blockentity.nativebridge.PortableShapeBlock.create(properties, spec)")
                .replace("new Block(blockProperties(spec))", "uk.co.enderfall.sdk.runtime.blockentity.nativebridge.PortableShapeBlock.create(blockProperties(spec), spec)")
                .replace("Block::new", "properties -> uk.co.enderfall.sdk.runtime.blockentity.nativebridge.PortableShapeBlock.create(properties, spec)");
    }

    static String emit(PlatformOperation operation, NativePlatformPolicy policy) throws BridgeGenerationException {
        return switch (operation) {
            case REGISTER_ITEM -> switch (policy) {
                case FABRIC_LEGACY, FABRIC_UNKEYED -> """
                        @Override
                        public void registerItem(ResourceId id, ItemSpec spec) {
                            Item item = Registry.register(BuiltInRegistries.ITEM, location(id),
                                    new uk.co.enderfall.sdk.runtime.item.nativebridge.PortableSdkItem(itemProperties(spec), spec));
                            items.put(id, item);
                        }
                    
                    """;
                case FABRIC_KEYED -> """
                        @Override
                        public void registerItem(ResourceId id, ItemSpec spec) {
                            ResourceLocation location = location(id);
                            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, location);
                            Item.Properties properties = itemProperties(spec).setId(key);
                            Item item = Registry.register(BuiltInRegistries.ITEM, key,
                                    new uk.co.enderfall.sdk.runtime.item.nativebridge.PortableSdkItem(properties, spec));
                            items.put(id, item);
                        }
                    
                    """;
                case FABRIC_IDENTIFIER -> """
                        @Override
                        public void registerItem(ResourceId id, ItemSpec spec) {
                            Identifier identifier = identifier(id);
                            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, identifier);
                            Item.Properties properties = itemProperties(spec).setId(key);
                            Item item = Registry.register(BuiltInRegistries.ITEM, key,
                                    new uk.co.enderfall.sdk.runtime.item.nativebridge.PortableSdkItem(properties, spec));
                            items.put(id, item);
                        }
                    
                    """;
                case LEGACY_FML -> """
                        @Override
                        public void registerItem(ResourceId id, ItemSpec spec) {
                            RegistryObject<Item> item = itemRegister.register(id.path(), () ->
                                    new uk.co.enderfall.sdk.runtime.item.nativebridge.PortableSdkItem(itemProperties(spec), spec));
                            items.put(id, item);
                        }
                    
                    """;
                case NEOFORGE -> """
                        @Override
                        public void registerItem(ResourceId id, ItemSpec spec) {
                            var item = itemRegister.registerItem(id.path(), properties ->
                                    new uk.co.enderfall.sdk.runtime.item.nativebridge.PortableSdkItem(properties, spec),
                                    itemProperties(spec));
                            items.put(id, item);
                        }
                    
                    """;
                case NEOFORGE_IDENTIFIER -> """
                        @Override
                        public void registerItem(ResourceId id, ItemSpec spec) {
                            var item = itemRegister.registerItem(id.path(), properties ->
                                    new uk.co.enderfall.sdk.runtime.item.nativebridge.PortableSdkItem(properties, spec),
                                    () -> itemProperties(spec));
                            items.put(id, item);
                        }
                    
                    """;
            };
            case REGISTER_BLOCK -> switch (policy) {
                case FABRIC_LEGACY, FABRIC_UNKEYED -> """
                        @Override
                        public void registerBlock(ResourceId id, BlockSpec spec, ItemSpec blockItemSpec) {
                            ResourceLocation location = location(id);
                            BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                                    .strength(spec.hardness(), spec.resistance())
                                    .friction(spec.friction())
                                    .jumpFactor(spec.jumpFactor())
                                    .lightLevel(state -> spec.luminance())
                                    .sound(sound(spec));
                            if (spec.requiresTool()) {
                                properties.requiresCorrectToolForDrops();
                            }
                            Block block = Registry.register(BuiltInRegistries.BLOCK, location, new Block(properties));
                            blocks.put(id, block);
                            if (blockItemSpec != null) {
                                Item item = Registry.register(BuiltInRegistries.ITEM, location,
                                        new BlockItem(block, itemProperties(blockItemSpec)));
                                items.put(id, item);
                            }
                        }
                    
                    """;
                case FABRIC_KEYED -> """
                        @Override
                        public void registerBlock(ResourceId id, BlockSpec spec, ItemSpec blockItemSpec) {
                            ResourceLocation location = location(id);
                            ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, location);
                            BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                                    .setId(blockKey)
                                    .strength(spec.hardness(), spec.resistance())
                                    .friction(spec.friction())
                                    .jumpFactor(spec.jumpFactor())
                                    .lightLevel(state -> spec.luminance())
                                    .sound(sound(spec));
                            if (spec.requiresTool()) {
                                properties.requiresCorrectToolForDrops();
                            }
                            Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, new Block(properties));
                            blocks.put(id, block);
                            if (blockItemSpec != null) {
                                ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, location);
                                Item.Properties itemProperties = itemProperties(blockItemSpec).setId(itemKey).useBlockDescriptionPrefix();
                                Item item = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, itemProperties));
                                items.put(id, item);
                            }
                        }
                    
                    """;
                case FABRIC_IDENTIFIER -> """
                        @Override
                        public void registerBlock(ResourceId id, BlockSpec spec, ItemSpec blockItemSpec) {
                            Identifier identifier = identifier(id);
                            ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, identifier);
                            BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                                    .setId(blockKey)
                                    .strength(spec.hardness(), spec.resistance())
                                    .friction(spec.friction())
                                    .jumpFactor(spec.jumpFactor())
                                    .lightLevel(state -> spec.luminance())
                                    .sound(sound(spec));
                            if (spec.requiresTool()) {
                                properties.requiresCorrectToolForDrops();
                            }
                            Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, new Block(properties));
                            blocks.put(id, block);
                            if (blockItemSpec != null) {
                                ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, identifier);
                                Item.Properties itemProperties = itemProperties(blockItemSpec).setId(itemKey)
                                        .useBlockDescriptionPrefix();
                                Item item = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, itemProperties));
                                items.put(id, item);
                            }
                        }
                    
                    """;
                case LEGACY_FML -> """
                        @Override
                        public void registerBlock(ResourceId id, BlockSpec spec, ItemSpec blockItemSpec) {
                            RegistryObject<Block> block = blockRegister.register(id.path(), () -> new Block(blockProperties(spec)));
                            blocks.put(id, block);
                            if (blockItemSpec != null) {
                                RegistryObject<Item> item = itemRegister.register(id.path(),
                                        () -> new BlockItem(block.get(), itemProperties(blockItemSpec)));
                                items.put(id, item);
                            }
                        }
                    
                    """;
                case NEOFORGE -> """
                        @Override
                        public void registerBlock(ResourceId id, BlockSpec spec, ItemSpec blockItemSpec) {
                            var block = blockRegister.registerBlock(id.path(), Block::new, blockProperties(spec));
                            blocks.put(id, block);
                            if (blockItemSpec != null) {
                                var blockItem = itemRegister.registerSimpleBlockItem(id.path(), block, itemProperties(blockItemSpec));
                                items.put(id, blockItem);
                            }
                        }
                    
                    """;
                case NEOFORGE_IDENTIFIER -> """
                        @Override
                        public void registerBlock(ResourceId id, BlockSpec spec, ItemSpec blockItemSpec) {
                            var block = blockRegister.registerBlock(id.path(), Block::new, () -> blockProperties(spec));
                            blocks.put(id, block);
                            if (blockItemSpec != null) {
                                var blockItem = itemRegister.registerSimpleBlockItem(
                                        id.path(), block, () -> itemProperties(blockItemSpec));
                                items.put(id, blockItem);
                            }
                        }
                    
                    """;
            };
            case REGISTER_CREATIVE_TAB -> switch (policy) {
                case FABRIC_LEGACY, FABRIC_UNKEYED -> """
                        @Override
                        public void registerCreativeTab(ResourceId id, CreativeTabSpec spec) {
                            Item icon = requireItem(spec.icon().id());
                            CreativeModeTab tab = FabricItemGroup.builder()
                                    .title(Component.translatable(spec.titleTranslationKey()))
                                    .icon(() -> new ItemStack(icon))
                                    .displayItems((parameters, output) -> spec.entries().forEach(entry ->
                                            output.accept(requireItem(entry.id()))))
                                    .build();
                            Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, location(id), tab);
                        }
                    
                    """;
                case FABRIC_KEYED -> """
                        @Override
                        public void registerCreativeTab(ResourceId id, CreativeTabSpec spec) {
                            Item icon = requireItem(spec.icon().id());
                            CreativeModeTab tab = FabricItemGroup.builder()
                                    .title(Component.translatable(spec.titleTranslationKey()))
                                    .icon(() -> new ItemStack(icon))
                                    .displayItems((parameters, output) -> spec.entries().forEach(entry -> output.accept(requireItem(entry.id()))))
                                    .build();
                            Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, location(id), tab);
                        }
                    
                    """;
                case FABRIC_IDENTIFIER -> """
                        @Override
                        public void registerCreativeTab(ResourceId id, CreativeTabSpec spec) {
                            Item icon = requireItem(spec.icon().id());
                            CreativeModeTab tab = FabricCreativeModeTab.builder()
                                    .title(Component.translatable(spec.titleTranslationKey()))
                                    .icon(() -> new ItemStack(icon))
                                    .displayItems((parameters, output) -> spec.entries().forEach(entry ->
                                            output.accept(requireItem(entry.id()))))
                                    .build();
                            Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, identifier(id), tab);
                        }
                    
                    """;
                case LEGACY_FML, NEOFORGE, NEOFORGE_IDENTIFIER -> """
                        @Override
                        public void registerCreativeTab(ResourceId id, CreativeTabSpec spec) {
                            creativeTabRegister.register(id.path(), () -> CreativeModeTab.builder()
                                    .title(Component.translatable(spec.titleTranslationKey()))
                                    .icon(() -> new ItemStack(requireItem(spec.icon().id())))
                                    .displayItems((parameters, output) -> spec.entries().forEach(entry ->
                                            output.accept(requireItem(entry.id()))))
                                    .build());
                        }
                    
                    """;
            };
            case REGISTER_COMMAND -> switch (policy) {
                case FABRIC_LEGACY, FABRIC_UNKEYED, LEGACY_FML -> """
                        @Override public void registerCommand(CommandSpec command) { ${CommandBridge}.register(command); }
                    
                    """;
                case FABRIC_KEYED, FABRIC_IDENTIFIER, NEOFORGE, NEOFORGE_IDENTIFIER -> """
                        @Override
                        public void registerCommand(CommandSpec command) {
                            ${CommandBridge}.register(command);
                        }
                    
                    """;
            };
            case REGISTER_WORKBENCH_RECIPE_TYPE -> switch (policy) {
                case FABRIC_LEGACY -> """
                        @Override
                        public void registerWorkbenchRecipeType(WorkbenchRecipeTypeRef recipeType) {
                            ResourceLocation id = location(recipeType.id());
                            RecipeType<${WorkbenchRecipe}> type = Registry.register(BuiltInRegistries.RECIPE_TYPE, id,
                                    new RecipeType<>() {
                                        @Override public String toString() { return id.toString(); }
                                    });
                            RecipeSerializer<${WorkbenchRecipe}> serializer = Registry.register(
                                    BuiltInRegistries.RECIPE_SERIALIZER, id,
                                    new ${WorkbenchRecipe}.Serializer(type, recipeType.inputSlots()));
                            recipeTypes.put(recipeType.id(), new ${RecipeBinding}(type, serializer, recipeType.inputSlots()));
                        }
                    
                    """;
                case FABRIC_UNKEYED, FABRIC_KEYED -> """
                        @Override
                        public void registerWorkbenchRecipeType(WorkbenchRecipeTypeRef recipeType) {
                            ResourceId id = recipeType.id();
                            if (recipeTypes.containsKey(id)) throw new IllegalStateException("[" + modId + "] Duplicate recipe type " + id);
                            AtomicReference<${RecipeBinding}> bindingReference = new AtomicReference<>();
                            RecipeType<${WorkbenchRecipe}> type = Registry.register(BuiltInRegistries.RECIPE_TYPE, location(id),
                                    new RecipeType<>() { @Override public String toString() { return id.toString(); } });
                            RecipeSerializer<${WorkbenchRecipe}> serializer = Registry.register(
                                    BuiltInRegistries.RECIPE_SERIALIZER, location(id),
                                    ${WorkbenchRecipe}.serializer(() -> requireRecipeBinding(bindingReference, id)));
                            ${RecipeBinding} binding = new ${RecipeBinding}(type, serializer, recipeType.inputSlots());
                            bindingReference.set(binding);
                            recipeTypes.put(id, binding);
                        }
                    
                    """;
                case FABRIC_IDENTIFIER -> """
                        @Override
                        public void registerWorkbenchRecipeType(WorkbenchRecipeTypeRef recipeType) {
                            ResourceId id = recipeType.id();
                            if (recipeTypes.containsKey(id)) throw new IllegalStateException("[" + modId + "] Duplicate recipe type " + id);
                            AtomicReference<${RecipeBinding}> bindingReference = new AtomicReference<>();
                            RecipeType<${WorkbenchRecipe}> type = Registry.register(BuiltInRegistries.RECIPE_TYPE, identifier(id),
                                    new RecipeType<>() { @Override public String toString() { return id.toString(); } });
                            RecipeSerializer<${WorkbenchRecipe}> serializer = Registry.register(
                                    BuiltInRegistries.RECIPE_SERIALIZER, identifier(id),
                                    ${WorkbenchRecipe}.serializer(() -> requireRecipeBinding(bindingReference, id)));
                            ${RecipeBinding} binding = new ${RecipeBinding}(type, serializer, recipeType.inputSlots());
                            bindingReference.set(binding);
                            recipeTypes.put(id, binding);
                        }
                    
                    """;
                case LEGACY_FML -> """
                        @Override
                        public void registerWorkbenchRecipeType(WorkbenchRecipeTypeRef recipeType) {
                            ResourceId id = recipeType.id();
                            if (recipeTypes.containsKey(id)) {
                                throw new IllegalStateException("[" + modId + "] Duplicate workbench recipe type " + id);
                            }
                            AtomicReference<${RecipeBinding}> bindingReference = new AtomicReference<>();
                            RegistryObject<RecipeType<${WorkbenchRecipe}>> type = recipeTypeRegister.register(id.path(), () ->
                                    new RecipeType<>() {
                                        @Override public String toString() { return id.toString(); }
                                    });
                            RegistryObject<RecipeSerializer<${WorkbenchRecipe}>> serializer = recipeSerializerRegister.register(
                                    id.path(), () -> new ${WorkbenchRecipe}.Serializer(
                                            requireRecipeBinding(bindingReference, id)));
                            ${RecipeBinding} binding = new ${RecipeBinding}(
                                    type, serializer, recipeType.inputSlots());
                            bindingReference.set(binding);
                            recipeTypes.put(id, binding);
                        }
                    
                    """;
                case NEOFORGE -> """
                        @Override
                        public void registerWorkbenchRecipeType(WorkbenchRecipeTypeRef recipeType) {
                            ResourceId id = recipeType.id();
                            if (recipeTypes.containsKey(id)) {
                                throw new IllegalStateException("[" + modId + "] Duplicate workbench recipe type " + id);
                            }
                            AtomicReference<${RecipeBinding}> bindingReference = new AtomicReference<>();
                            Supplier<RecipeType<${WorkbenchRecipe}>> type = recipeTypeRegister.register(id.path(), () ->
                                    new RecipeType<>() {
                                        @Override public String toString() { return id.toString(); }
                                    });
                            Supplier<RecipeSerializer<${WorkbenchRecipe}>> serializer = recipeSerializerRegister.register(
                                    id.path(), () -> ${WorkbenchRecipe}.serializer(
                                            requireRecipeBinding(bindingReference, id)));
                            ${RecipeBinding} binding = new ${RecipeBinding}(type, serializer, recipeType.inputSlots());
                            bindingReference.set(binding);
                            recipeTypes.put(id, binding);
                        }
                    
                    """;
                case NEOFORGE_IDENTIFIER -> """
                        @Override
                        public void registerWorkbenchRecipeType(WorkbenchRecipeTypeRef recipeType) {
                            ResourceId id = recipeType.id();
                            if (recipeTypes.containsKey(id)) {
                                throw new IllegalStateException("[" + modId + "] Duplicate workbench recipe type " + id);
                            }
                    
                            Identifier nativeId = identifier(id);
                            Supplier<RecipeType<${WorkbenchRecipe}>> type = recipeTypeRegister.register(
                                    id.path(), () -> RecipeType.simple(nativeId));
                            AtomicReference<${RecipeBinding}> bindingReference = new AtomicReference<>();
                            Supplier<RecipeSerializer<${WorkbenchRecipe}>> serializer = recipeSerializerRegister.register(
                                    id.path(), () -> {
                                        ${RecipeBinding} binding = bindingReference.get();
                                        if (binding == null) {
                                            throw new IllegalStateException("[" + modId + "] Recipe serializer initialized too early for " + id);
                                        }
                                        return new RecipeSerializer<>(${WorkbenchRecipe}.codec(binding),
                                                ${WorkbenchRecipe}.streamCodec(binding));
                                    });
                            ${RecipeBinding} binding = new ${RecipeBinding}(
                                    nativeId, type, serializer, recipeType.inputSlots());
                            bindingReference.set(binding);
                            recipeTypes.put(id, binding);
                        }
                    
                    """;
            };
            case REGISTER_WORKBENCH -> switch (policy) {
                case FABRIC_LEGACY -> """
                        @Override
                        public void registerWorkbench(PortableWorkbenchDefinition definition) {
                            ${RecipeBinding} recipes = recipeTypes.get(definition.spec().recipeType().id());
                            if (recipes == null) {
                                throw new IllegalStateException("[" + modId + "] Workbench references an unregistered recipe type "
                                        + definition.spec().recipeType().id());
                            }
                            java.util.concurrent.atomic.AtomicReference<MenuType<${WorkbenchMenu}>> typeReference =
                                    new java.util.concurrent.atomic.AtomicReference<>();
                            java.util.concurrent.atomic.AtomicReference<${WorkbenchBinding}> bindingReference =
                                    new java.util.concurrent.atomic.AtomicReference<>();
                            MenuType<${WorkbenchMenu}> menuType = Registry.register(BuiltInRegistries.MENU,
                                    location(definition.reference().id()), new MenuType<>((containerId, inventory) ->
                                            new ${WorkbenchMenu}(typeReference.get(), containerId, inventory,
                                                    bindingReference.get()), FeatureFlags.VANILLA_SET));
                            ${WorkbenchBinding} binding = new ${WorkbenchBinding}(definition, recipes, menuType);
                            typeReference.set(menuType);
                            bindingReference.set(binding);
                            workbenches.put(definition.reference().id(), binding);
                            if (platformInfo.environment() == Environment.CLIENT) {
                                ${ClientHooks}.registerWorkbench(menuType);
                            }
                        }
                    
                    """;
                case FABRIC_UNKEYED, FABRIC_KEYED -> """
                        @Override
                        public void registerWorkbench(PortableWorkbenchDefinition definition) {
                            ResourceId id = definition.reference().id();
                            ${RecipeBinding} recipes = recipeTypes.get(definition.spec().recipeType().id());
                            if (recipes == null) throw new IllegalStateException("[" + modId + "] Workbench " + id
                                    + " references unregistered recipe type " + definition.spec().recipeType().id());
                            if (workbenches.containsKey(id)) throw new IllegalStateException("[" + modId + "] Duplicate workbench " + id);
                            AtomicReference<${WorkbenchBinding}> bindingReference = new AtomicReference<>();
                            MenuType<${WorkbenchMenu}> menuType = Registry.register(BuiltInRegistries.MENU, location(id),
                                    new MenuType<>((containerId, inventory) -> new ${WorkbenchMenu}(containerId, inventory,
                                            requireWorkbenchBinding(bindingReference, id)), FeatureFlags.VANILLA_SET));
                            ${WorkbenchBinding} binding = new ${WorkbenchBinding}(definition, recipes, menuType);
                            bindingReference.set(binding);
                            workbenches.put(id, binding);
                            if (platformInfo.environment() == Environment.CLIENT) ${ClientHooks}.registerWorkbench(menuType);
                        }
                    
                    """;
                case FABRIC_IDENTIFIER -> """
                        @Override
                        public void registerWorkbench(PortableWorkbenchDefinition definition) {
                            ResourceId id = definition.reference().id();
                            ${RecipeBinding} recipes = recipeTypes.get(definition.spec().recipeType().id());
                            if (recipes == null) throw new IllegalStateException("[" + modId + "] Workbench " + id
                                    + " references unregistered recipe type " + definition.spec().recipeType().id());
                            if (workbenches.containsKey(id)) throw new IllegalStateException("[" + modId + "] Duplicate workbench " + id);
                            AtomicReference<${WorkbenchBinding}> bindingReference = new AtomicReference<>();
                            MenuType<${WorkbenchMenu}> menuType = Registry.register(BuiltInRegistries.MENU, identifier(id),
                                    new MenuType<>((containerId, inventory) -> new ${WorkbenchMenu}(containerId, inventory,
                                            requireWorkbenchBinding(bindingReference, id)), FeatureFlags.VANILLA_SET));
                            ${WorkbenchBinding} binding = new ${WorkbenchBinding}(definition, recipes, menuType);
                            bindingReference.set(binding);
                            workbenches.put(id, binding);
                            if (platformInfo.environment() == Environment.CLIENT) ${ClientHooks}.registerWorkbench(menuType);
                        }
                    
                    """;
                case LEGACY_FML -> """
                        @Override
                        public void registerWorkbench(PortableWorkbenchDefinition definition) {
                            ResourceId id = definition.reference().id();
                            ${RecipeBinding} recipes = recipeTypes.get(definition.spec().recipeType().id());
                            if (recipes == null) {
                                throw new IllegalStateException("[" + modId + "] Workbench " + id
                                        + " references unregistered recipe type " + definition.spec().recipeType().id());
                            }
                            if (workbenches.containsKey(id)) {
                                throw new IllegalStateException("[" + modId + "] Duplicate workbench " + id);
                            }
                            AtomicReference<${WorkbenchBinding}> bindingReference = new AtomicReference<>();
                            RegistryObject<MenuType<${WorkbenchMenu}>> menuType = menuRegister.register(id.path(), () ->
                                    new MenuType<>((containerId, inventory) -> new ${WorkbenchMenu}(
                                            containerId, inventory, requireWorkbenchBinding(bindingReference, id)),
                                            FeatureFlags.VANILLA_SET));
                            ${WorkbenchBinding} binding = new ${WorkbenchBinding}(definition, recipes, menuType);
                            bindingReference.set(binding);
                            workbenches.put(id, binding);
                        }
                    
                    """;
                case NEOFORGE -> """
                        @Override
                        public void registerWorkbench(PortableWorkbenchDefinition definition) {
                            ResourceId id = definition.reference().id();
                            ${RecipeBinding} recipes = recipeTypes.get(definition.spec().recipeType().id());
                            if (recipes == null) {
                                throw new IllegalStateException("[" + modId + "] Workbench " + id
                                        + " references unregistered recipe type " + definition.spec().recipeType().id());
                            }
                            if (workbenches.containsKey(id)) {
                                throw new IllegalStateException("[" + modId + "] Duplicate workbench " + id);
                            }
                            AtomicReference<${WorkbenchBinding}> bindingReference = new AtomicReference<>();
                            Supplier<MenuType<${WorkbenchMenu}>> menuType = menuRegister.register(id.path(), () ->
                                    new MenuType<>((containerId, inventory) -> new ${WorkbenchMenu}(
                                            containerId, inventory, requireWorkbenchBinding(bindingReference, id)),
                                            FeatureFlags.VANILLA_SET));
                            ${WorkbenchBinding} binding = new ${WorkbenchBinding}(definition, recipes, menuType);
                            bindingReference.set(binding);
                            workbenches.put(id, binding);
                        }
                    
                    """;
                case NEOFORGE_IDENTIFIER -> """
                        @Override
                        public void registerWorkbench(PortableWorkbenchDefinition definition) {
                            ResourceId id = definition.reference().id();
                            if (workbenches.containsKey(id)) {
                                throw new IllegalStateException("[" + modId + "] Duplicate workbench " + id);
                            }
                            ${RecipeBinding} recipes = recipeTypes.get(definition.spec().recipeType().id());
                            if (recipes == null) {
                                throw new IllegalStateException("[" + modId + "] Workbench " + id
                                        + " references unregistered recipe type " + definition.spec().recipeType().id());
                            }
                    
                            AtomicReference<${WorkbenchBinding}> bindingReference = new AtomicReference<>();
                            Supplier<MenuType<${WorkbenchMenu}>> menuType = menuRegister.register(id.path(), () ->
                                    new MenuType<>((containerId, inventory) -> new ${WorkbenchMenu}(
                                            containerId, inventory, requireBinding(bindingReference, id)), FeatureFlags.VANILLA_SET));
                            ${WorkbenchBinding} binding = new ${WorkbenchBinding}(definition, recipes, menuType);
                            bindingReference.set(binding);
                            workbenches.put(id, binding);
                        }
                    
                    """;
            };
            case OPEN_WORKBENCH -> switch (policy) {
                case FABRIC_LEGACY -> """
                        @Override
                        public void openWorkbench(java.util.UUID playerId, PortableWorkbenchDefinition definition) {
                            ${WorkbenchBinding} binding = workbenches.get(definition.reference().id());
                            if (binding == null) {
                                throw new IllegalArgumentException("[" + modId + "] Unknown workbench " + definition.reference().id());
                            }
                            ServerPlayer player = requireOnlinePlayer(playerId);
                            player.openMenu(new SimpleMenuProvider((containerId, inventory, ignored) ->
                                    new ${WorkbenchMenu}(binding.menuType(), containerId, inventory, binding),
                                    Component.literal(definition.spec().title())));
                        }
                    
                    """;
                case FABRIC_UNKEYED, FABRIC_KEYED, FABRIC_IDENTIFIER -> """
                        @Override
                        public void openWorkbench(java.util.UUID playerId, PortableWorkbenchDefinition definition) {
                            ${WorkbenchBinding} binding = workbenches.get(definition.reference().id());
                            if (binding == null) throw new IllegalStateException("[" + modId + "] Unknown workbench " + definition.reference().id());
                            requireOnlinePlayer(playerId).openMenu(new SimpleMenuProvider(
                                    (containerId, inventory, ignored) -> new ${WorkbenchMenu}(containerId, inventory, binding),
                                    Component.literal(definition.spec().title())));
                        }
                    
                    """;
                case LEGACY_FML, NEOFORGE -> """
                        @Override
                        public void openWorkbench(java.util.UUID playerId, PortableWorkbenchDefinition definition) {
                            ${WorkbenchBinding} binding = workbenches.get(definition.reference().id());
                            if (binding == null) {
                                throw new IllegalStateException("[" + modId + "] Unknown workbench " + definition.reference().id());
                            }
                            requireOnlinePlayer(playerId).openMenu(new SimpleMenuProvider(
                                    (containerId, inventory, ignored) -> new ${WorkbenchMenu}(containerId, inventory, binding),
                                    Component.literal(definition.spec().title())));
                        }
                    
                    """;
                case NEOFORGE_IDENTIFIER -> """
                        @Override
                        public void openWorkbench(java.util.UUID playerId, PortableWorkbenchDefinition definition) {
                            ${WorkbenchBinding} binding = workbenches.get(definition.reference().id());
                            if (binding == null) {
                                throw new IllegalStateException("[" + modId + "] Unknown workbench " + definition.reference().id());
                            }
                            ServerPlayer player = requireOnlinePlayer(playerId);
                            player.openMenu(new SimpleMenuProvider(
                                    (containerId, inventory, ignored) -> new ${WorkbenchMenu}(containerId, inventory, binding),
                                    Component.literal(definition.spec().title())));
                        }
                    
                    """;
            };
            case REQUIRE_RECIPE_BINDING -> switch (policy) {
                case FABRIC_UNKEYED, FABRIC_KEYED, FABRIC_IDENTIFIER -> """
                        private static ${RecipeBinding} requireRecipeBinding(
                                AtomicReference<${RecipeBinding}> reference, ResourceId id) {
                            ${RecipeBinding} binding = reference.get();
                            if (binding == null) throw new IllegalStateException("Recipe serializer initialized too early for " + id);
                            return binding;
                        }
                    
                    """;
                case LEGACY_FML, NEOFORGE -> """
                        private static ${RecipeBinding} requireRecipeBinding(
                                AtomicReference<${RecipeBinding}> reference, ResourceId id) {
                            ${RecipeBinding} binding = reference.get();
                            if (binding == null) {
                                throw new IllegalStateException("Recipe serializer initialized too early for " + id);
                            }
                            return binding;
                        }
                    
                    """;
                default -> throw new BridgeGenerationException(operation + " is unavailable for " + policy);
            };
            case REQUIRE_WORKBENCH_BINDING -> switch (policy) {
                case FABRIC_UNKEYED, FABRIC_KEYED, FABRIC_IDENTIFIER -> """
                        private static ${WorkbenchBinding} requireWorkbenchBinding(
                                AtomicReference<${WorkbenchBinding}> reference, ResourceId id) {
                            ${WorkbenchBinding} binding = reference.get();
                            if (binding == null) throw new IllegalStateException("Workbench menu initialized too early for " + id);
                            return binding;
                        }
                    
                    """;
                case LEGACY_FML, NEOFORGE -> """
                        private static ${WorkbenchBinding} requireWorkbenchBinding(
                                AtomicReference<${WorkbenchBinding}> reference, ResourceId id) {
                            ${WorkbenchBinding} binding = reference.get();
                            if (binding == null) {
                                throw new IllegalStateException("Workbench menu initialized too early for " + id);
                            }
                            return binding;
                        }
                    
                    """;
                default -> throw new BridgeGenerationException(operation + " is unavailable for " + policy);
            };
            case REQUIRE_BINDING -> switch (policy) {
                case NEOFORGE_IDENTIFIER -> """
                        private static ${WorkbenchBinding} requireBinding(
                                AtomicReference<${WorkbenchBinding}> reference, ResourceId id) {
                            ${WorkbenchBinding} binding = reference.get();
                            if (binding == null) {
                                throw new IllegalStateException("Workbench menu initialized too early for " + id);
                            }
                            return binding;
                        }
                    
                    """;
                default -> throw new BridgeGenerationException(operation + " is unavailable for " + policy);
            };
            case ITEM_PROPERTIES -> """
                        private static Item.Properties itemProperties(ItemSpec spec) {
                            Item.Properties properties = new Item.Properties().stacksTo(spec.maxStackSize())
                                    .rarity(switch (spec.rarity()) {
                                        case COMMON -> net.minecraft.world.item.Rarity.COMMON;
                                        case UNCOMMON -> net.minecraft.world.item.Rarity.UNCOMMON;
                                        case RARE -> net.minecraft.world.item.Rarity.RARE;
                                        case EPIC -> net.minecraft.world.item.Rarity.EPIC;
                                    });
                            if (spec.durability() > 0) {
                                properties.durability(spec.durability());
                            }
                            if (spec.fireResistant()) {
                                properties.fireResistant();
                            }
                            return properties;
                        }
                    
                    """;
            case BLOCK_PROPERTIES -> switch (policy) {
                case LEGACY_FML, NEOFORGE, NEOFORGE_IDENTIFIER -> """
                        private static BlockBehaviour.Properties blockProperties(BlockSpec spec) {
                            BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                                    .strength(spec.hardness(), spec.resistance())
                                    .friction(spec.friction())
                                    .jumpFactor(spec.jumpFactor())
                                    .lightLevel(state -> spec.luminance())
                                    .sound(sound(spec));
                            if (spec.requiresTool()) {
                                properties.requiresCorrectToolForDrops();
                            }
                            return properties;
                        }
                    
                    """;
                default -> throw new BridgeGenerationException(operation + " is unavailable for " + policy);
            };
            case SOUND -> """
                        private static SoundType sound(BlockSpec spec) {
                            return switch (spec.sound()) {
                                case STONE -> SoundType.STONE;
                                case WOOD -> SoundType.WOOD;
                                case METAL -> SoundType.METAL;
                                case GLASS -> SoundType.GLASS;
                                case WOOL -> SoundType.WOOL;
                                case GRAVEL -> SoundType.GRAVEL;
                                case SAND -> SoundType.SAND;
                            };
                        }
                    
                    """;
            default -> throw new BridgeGenerationException("Operation " + operation + " does not belong to PlatformRegistrationSources");
        };
    }
}
