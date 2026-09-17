package uk.co.enderfall.sdk.bridge;

/** The 26.2 value-storage and removal ABI, applied only to authored storage source. */
final class BlockEntity26Sources {
    private BlockEntity26Sources() { }

    static String names(String source) {
        return source.replace("ResourceLocation", "Identifier").replace(".serverLevel()", ".level()")
                .replace("location(id)", "identifier(id)")
                .replace(".dimension().location()", ".dimension().identifier()");
    }

    static String adapt(String source) {
        return source.replace("ResourceLocation", "Identifier")
                .replace("level.isClientSide", "level.isClientSide()")
                .replace("level.dimension().location()", "level.dimension().identifier()")
                .replace("BlockEntityType.Builder.of(\n                        (pos, state) -> new StoredBlockEntity(binding, pos, state), binding.block).build(null)",
                        "new BlockEntityType<>((pos, state) -> new StoredBlockEntity(binding, pos, state), java.util.Set.of(binding.block))")
                .replace("BlockEntityType.Builder.of((pos, state) -> new StoredBlockEntity(binding, pos, state), block.get()).build(null)",
                        "new BlockEntityType<>((pos, state) -> new StoredBlockEntity(binding, pos, state), java.util.Set.of(block.get()))")
                .replace("protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries)",
                        "protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput tag)")
                .replace("super.saveAdditional(tag, registries);", "super.saveAdditional(tag);")
                .replace("protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries)",
                        "protected void loadAdditional(net.minecraft.world.level.storage.ValueInput tag)")
                .replace("super.loadAdditional(tag, registries);", "super.loadAdditional(tag);")
                .replace("serializationRegistries = registries;", "serializationRegistries = level == null ? null : level.registryAccess();")
                .replace("serializationRegistries = level == null ? null : level.registryAccess();\n        try {\n            PortableProcessingCycle.State restoredProcessing",
                        "serializationRegistries = snapshotLookup(tag);\n        try {\n            PortableProcessingCycle.State restoredProcessing")
                .replace("tag.putByteArray(SAVE_KEY, storage.save());", "tag.store(SAVE_KEY, com.mojang.serialization.Codec.BYTE_BUFFER, java.nio.ByteBuffer.wrap(storage.save()));")
                .replace("tag.putByteArray(PROCESS_KEY, ProcessingStateCodec.encode(processing));", "tag.store(PROCESS_KEY, com.mojang.serialization.Codec.BYTE_BUFFER, java.nio.ByteBuffer.wrap(ProcessingStateCodec.encode(processing)));")
                .replace("tag.contains(SAVE_KEY, Tag.TAG_BYTE_ARRAY)", "hasByteArray(tag, SAVE_KEY)")
                .replace("tag.contains(PROCESS_KEY, Tag.TAG_BYTE_ARRAY)", "hasByteArray(tag, PROCESS_KEY)")
                .replace("tag.contains(SAVE_KEY)", "tag.read(SAVE_KEY, com.mojang.serialization.Codec.PASSTHROUGH).isPresent()")
                .replace("tag.contains(PROCESS_KEY)", "tag.read(PROCESS_KEY, com.mojang.serialization.Codec.PASSTHROUGH).isPresent()")
                .replace("tag.getByteArray(PROCESS_KEY)", "readBytes(tag, PROCESS_KEY)")
                .replace("tag.getByteArray(SAVE_KEY)", "readBytes(tag, SAVE_KEY)")
                .replace("private HolderLookup.Provider registries() {", helpers() + "\n    private HolderLookup.Provider registries() {");
    }

    private static String helpers() {
        return """
                    // The opaque SDK snapshot contains nested ItemStack codecs. Loading precedes setLevel,
                    // so these codecs must use the caller's registry context, not a global registry.
                    @SuppressWarnings("deprecation")
                    private static HolderLookup.Provider snapshotLookup(net.minecraft.world.level.storage.ValueInput input) {
                        return input.lookup();
                    }
                    // PASSTHROUGH preserves wrong-typed entries so corrupt saves never look like new blocks.
                    private static boolean hasByteArray(net.minecraft.world.level.storage.ValueInput input, String key) {
                        return input.read(key, com.mojang.serialization.Codec.PASSTHROUGH)
                                .map(value -> value.convert(NbtOps.INSTANCE).getValue() instanceof ByteArrayTag).orElse(false);
                    }
                    private static byte[] readBytes(net.minecraft.world.level.storage.ValueInput input, String key) {
                        var value = input.read(key, com.mojang.serialization.Codec.PASSTHROUGH).orElseThrow();
                        Tag tag = value.convert(NbtOps.INSTANCE).getValue();
                        if (!(tag instanceof ByteArrayTag bytes)) throw new IllegalArgumentException("Wrong EnderFall storage type");
                        return bytes.getAsByteArray();
                    }
                    @Override
                    public void preRemoveSideEffects(BlockPos pos, BlockState next) {
                        if (level != null && !level.isClientSide() && !getBlockState().is(next.getBlock())) {
                            Containers.dropContents(level, pos, inventory);
                            inventory.clearContent();
                            level.updateNeighbourForOutputSignal(pos, getBlockState().getBlock());
                        }
                        super.preRemoveSideEffects(pos, next);
                    }
                """;
    }
}
