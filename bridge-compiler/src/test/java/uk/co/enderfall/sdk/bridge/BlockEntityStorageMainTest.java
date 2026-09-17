package uk.co.enderfall.sdk.bridge;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BlockEntityStorageMainTest {
    @TempDir Path directory;

    @Test void usesExplicitRegistrationPolicyAndPreservesSharedStorage() throws Exception {
        for (String target : new String[] {"1.21.1-fabric", "1.21.4-fabric"}) {
            Path root = directory.resolve(target);
            BlockEntityStorageMain.main(new String[] {target, root.toString()});
            Path file = root.resolve("uk/co/enderfall/sdk/runtime/blockentity/nativebridge/StoredBlockEntity.java");
            String source = Files.readString(file);
            assertTrue(source.contains("inventory.setItem(slot, storage.stack(slot))"));
            assertTrue(source.contains("ProcessingStateCodec.decode(tag.getByteArray(PROCESS_KEY))"));
            assertTrue(source.contains("implements net.minecraft.world.WorldlyContainer"));
            assertTrue(source.contains("on " + target + " at"));
            assertEquals(target.equals("1.21.4-fabric"), source.contains("properties.setId("));
            assertEquals(target.equals("1.21.1-fabric"), source.contains("BlockEntityType.Builder.of("));
            assertFalse(source.contains("${"));
            BlockEntityStorageMain.main(new String[] {target, root.toString()});
            assertEquals(source, Files.readString(file));
        }
    }

    @Test void unreviewedTargetsFailBeforeWriting() {
        Path output = directory.resolve("complete");
        assertThrows(IllegalArgumentException.class, () -> BlockEntityPreviewMain.main(
                new String[] {"27.1-fabric", output.toString()}));
        assertFalse(Files.exists(output));
        assertThrows(IllegalArgumentException.class, () -> BlockEntityStorageMain.main(
                new String[] {"27.1-fabric", output.toString()}));
        assertFalse(Files.exists(output));
    }

    @Test void legacyTargetsGenerateCompleteSourcesWithReviewedLoaderHooks() throws Exception {
        for (String loader : new String[] {"fabric", "forge", "neoforge"}) {
            String target = "1.20.1-" + loader;
            Path output = directory.resolve(target);
            BlockEntityPreviewMain.main(new String[] {target, output.toString()});
            var policy = BlockEntityNativePolicy.require(target);
            Path root = output.resolve("uk/co/enderfall/sdk/runtime/" + policy.runtimePackage().replace('.', '/'));
            String client = Files.readString(root.resolve(policy.prefix() + "TimedWorkbenchClient.java"));
            assertFalse(client.contains("RegisterMenuScreensEvent"));
            assertEquals(!policy.fabric(), client.contains("FMLClientSetupEvent"));
            assertEquals(!policy.fabric(), client.contains("event.enqueueWork"));
            String storage = Files.readString(output.resolve("uk/co/enderfall/sdk/runtime/blockentity/nativebridge/StoredBlockEntity.java"));
            assertTrue(storage.contains("public void load(CompoundTag tag)"));
            assertFalse(storage.contains(".getItems()"));
            assertTrue(storage.contains("containerStacks(inventory)"));
            try (var files = Files.walk(output)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    assertFalse(Files.readString(file).contains("${"), file.toString());
                }
            }
        }
    }

    @Test void complete1211PreviewUsesLegacyRecipeAndRegistrationRules() throws Exception {
        Path output = directory.resolve("complete1211");
        BlockEntityPreviewMain.main(new String[] {"1.21.1-fabric", output.toString()});
        Path root = output.resolve("uk/co/enderfall/sdk/runtime/fabric/v1_21_4");
        String processor = Files.readString(root.resolve("FabricTimedWorkbenchProcessor.java"));
        assertTrue(processor.contains("level.getRecipeManager().getRecipeFor("));
        assertTrue(processor.contains("hasCraftingRemainingItem()"));
        assertFalse(processor.contains("holder.id().location()"));
        String platform = Files.readString(root.resolve("Fabric1211PersistentPlatformAdapter.java"));
        assertTrue(platform.contains("Item.Properties itemProperties = itemProperties(itemSpec);"));
        assertFalse(platform.contains("${"));
    }

    @Test void valueStoragePreservesRegistryContextAndRejectsWrongTypedSnapshots() {
        for (String target : new String[] {"26.2-fabric", "26.2-neoforge"}) {
            String source = BlockEntityPreviewSources.emit(target);
            assertTrue(source.contains("serializationRegistries = snapshotLookup(tag);"));
            assertTrue(source.contains("value.convert(NbtOps.INSTANCE).getValue() instanceof ByteArrayTag"));
            assertTrue(source.contains("public void preRemoveSideEffects(BlockPos pos, BlockState next)"));
            assertTrue(source.contains("Containers.dropContents(level, pos, inventory)"));
            assertFalse(source.contains("void onRemove("));
            assertFalse(source.contains("ResourceLocation"));
            assertFalse(source.contains("BlockEntityType.Builder"));
            assertFalse(source.contains("${"));
        }
    }
}
