package uk.co.enderfall.sdk.bridge;

/** Native property copying for generated feature runtimes. */
final class BlockPropertyCopySources {
    private BlockPropertyCopySources() { }
    static String operations(BlockEntityNativePolicy policy) {
        String registry = policy.legacy() && !policy.fabric()
                ? "net.minecraftforge.registries.ForgeRegistries.BLOCKS" : "BuiltInRegistries.BLOCK";
        String lookup = policy.legacy() && !policy.fabric() || policy.modernRecipes() ? "getValue" : "get";
        String location = policy.unobfuscated() ? "identifier" : "location";
        String blockLookup = policy.fabric() ? "blocks.get(reference.id())"
                : "java.util.Optional.ofNullable(blocks.get(reference.id())).map(java.util.function.Supplier::get).orElse(null)";
        return """
                    @Override public boolean supportsBlockPropertyCopy() { return true; }
                    @Override public boolean supportsBlockShapes() { return true; }
                    @Override public boolean supportsHorizontalFacing() { return true; }
                    @Override public boolean supportsSixWayFacing() { return true; }
                    @Override public boolean supportsBlockStates() { return true; }
                    @Override public boolean supportsStateShapes() { return true; }
                    @Override
                    public java.util.Optional<uk.co.enderfall.sdk.api.block.PortableBlockState> blockState(
                            uk.co.enderfall.sdk.api.registry.BlockRef reference,
                            uk.co.enderfall.sdk.api.blockentity.BlockLocation targetLocation) {
                        MinecraftServer current = requireServer();
                        if (!current.isSameThread()) throw new IllegalStateException("Block state access must run on the server thread");
                        Block expected = @BLOCK_LOOKUP@;
                        if (!(expected instanceof uk.co.enderfall.sdk.runtime.blockentity.nativebridge.PortableShapeBlock portable))
                            throw new IllegalArgumentException("Unknown portable block " + reference.id());
                        var dimension = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                                @LOCATION@(targetLocation.dimension()));
                        var level = current.getLevel(dimension);
                        var pos = new net.minecraft.core.BlockPos(targetLocation.x(), targetLocation.y(), targetLocation.z());
                        if (level == null || level.isOutsideBuildHeight(pos)
                                || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return java.util.Optional.empty();
                        net.minecraft.world.level.block.state.BlockState nativeState = level.getBlockState(pos);
                        return nativeState.getBlock() == expected ? java.util.Optional.of(portable.portableState(nativeState))
                                : java.util.Optional.empty();
                    }
                    @Override
                    public boolean updateBlockState(uk.co.enderfall.sdk.api.registry.BlockRef reference,
                            uk.co.enderfall.sdk.api.blockentity.BlockLocation targetLocation,
                            java.util.function.UnaryOperator<uk.co.enderfall.sdk.api.block.PortableBlockState> change) {
                        MinecraftServer current = requireServer();
                        if (!current.isSameThread()) throw new IllegalStateException("Block state updates must run on the server thread");
                        Block expected = @BLOCK_LOOKUP@;
                        if (!(expected instanceof uk.co.enderfall.sdk.runtime.blockentity.nativebridge.PortableShapeBlock portable))
                            throw new IllegalArgumentException("Unknown portable block " + reference.id());
                        var dimension = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                                @LOCATION@(targetLocation.dimension()));
                        var level = current.getLevel(dimension);
                        var pos = new net.minecraft.core.BlockPos(targetLocation.x(), targetLocation.y(), targetLocation.z());
                        if (level == null || level.isOutsideBuildHeight(pos)
                                || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return false;
                        net.minecraft.world.level.block.state.BlockState nativeState = level.getBlockState(pos);
                        if (nativeState.getBlock() != expected) return false;
                        var before = portable.portableState(nativeState);
                        var after = java.util.Objects.requireNonNull(change.apply(before), "Block state change returned null");
                        net.minecraft.world.level.block.state.BlockState updated = portable.withPortableState(nativeState, after);
                        return updated != nativeState && level.setBlock(pos, updated, 3);
                    }
                    private static BlockBehaviour.Properties copiedBlockProperties(BlockSpec spec) {
                        BlockBehaviour.Properties properties;
                        if (spec.copySource().isPresent()) {
                            ResourceId source = spec.copySource().orElseThrow();
                            var id = @ID@;
                            if (!@REGISTRY@.containsKey(id)) throw new IllegalArgumentException("Missing block property source " + source);
                            var block = java.util.Objects.requireNonNull(@REGISTRY@.@LOOKUP@(id));
                            properties = BlockBehaviour.Properties.@COPY@(block);
                        } else properties = BlockBehaviour.Properties.of();
                        if (spec.overrides(BlockSpec.Property.STRENGTH)) properties.strength(spec.hardness(), spec.resistance());
                        if (spec.overrides(BlockSpec.Property.FRICTION)) properties.friction(spec.friction());
                        if (spec.overrides(BlockSpec.Property.JUMP_FACTOR)) properties.jumpFactor(spec.jumpFactor());
                        if (spec.overrides(BlockSpec.Property.LUMINANCE)) properties.lightLevel(state -> spec.luminance());
                        if (spec.overrides(BlockSpec.Property.SOUND)) properties.sound(sound(spec));
                        if (spec.overrides(BlockSpec.Property.TOOL) && spec.requiresTool()) properties.requiresCorrectToolForDrops();
                        return properties;
                    }

                """.replace("@ID@", policy.unobfuscated() ? "Identifier.parse(source.toString())"
                    : "java.util.Objects.requireNonNull(ResourceLocation.tryParse(source.toString()))")
                .replace("@REGISTRY@", registry).replace("@LOOKUP@", lookup)
                .replace("@LOCATION@", location)
                .replace("@BLOCK_LOOKUP@", blockLookup)
                .replace("@COPY@", policy.legacy() ? "copy" : "ofFullCopy");
    }
}
