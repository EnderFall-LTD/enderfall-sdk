package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BlockEntityPreviewSourcesTest {
    @TempDir Path directory;

    @Test void generatesDeterministicNativeHooksOutsideBaselineOutputs() throws Exception {
        BlockEntityPreviewMain.main(new String[] {"1.21.4-fabric", directory.toString()});
        Path source = directory.resolve("uk/co/enderfall/sdk/runtime/blockentity/nativebridge/StoredBlockEntity.java");
        String first = Files.readString(source);
        BlockEntityPreviewMain.main(new String[] {"1.21.4-fabric", directory.toString()});
        assertEquals(first, Files.readString(source));
        assertTrue(first.contains("FabricBlockEntityTypeBuilder.create("));
        assertTrue(first.contains("implements net.minecraft.world.WorldlyContainer"));
        assertTrue(first.contains("binding.ports.canInsert(slot, machinePortFace(face))"));
        assertTrue(first.contains("binding.ports.canExtract(slot, machinePortFace(face))"));
        assertTrue(first.contains("if (face == null) return new int[0]"));
        assertTrue(first.contains("binding.inventoryPorts.slots(inventoryPortFace(face))"));
        assertTrue(first.contains("binding.inventoryPorts.canInsert(inventoryPortFace(face), slot)"));
        assertTrue(first.contains("binding.inventoryPorts.canExtract(inventoryPortFace(face), slot)"));
        assertTrue(first.contains("InventoryAccessSpec.allFaces(spec.inventorySlots())"));
        assertTrue(first.contains("storage.restore(tag.getByteArray(SAVE_KEY))"));
        assertTrue(first.contains("inventory.setItem(slot, storage.stack(slot))"));
        assertTrue(first.contains("notifyInventoryCommit = () -> { super.setChanged(); notifyInventoryListeners(); }"));
        assertTrue(first.indexOf("ProcessingStateCodec.decode(tag.getByteArray(PROCESS_KEY))")
                < first.indexOf("storage.restore(tag.getByteArray(SAVE_KEY))"));
        assertTrue(first.contains("tag.putByteArray(PROCESS_KEY, ProcessingStateCodec.encode(processing))"));
        assertTrue(first.contains("tag.putByteArray(SAVE_KEY, storage.save())"));
        assertTrue(first.contains("registries().createSerializationContext(NbtOps.INSTANCE)"));
        assertTrue(first.contains("level == null || level.isClientSide"));
        assertTrue(first.contains("input.available() != 0"));
        assertTrue(first.contains("storage.replaceInventory(containerStacks(this))"));
        assertTrue(first.contains("level.getBlockEntity(worldPosition) == StoredBlockEntity.this"));
        assertTrue(first.contains("Containers.dropContents(level, pos, entity.inventory)"));
        assertTrue(first.contains("if (!removed.isEmpty()) setChanged()"));
        assertFalse(first.contains("storage.markPersisted("));
        assertTrue(first.contains("level.isClientSide || type != binding.type"));
        assertTrue(first.contains("binding.spec.serverTicker().isEmpty() && binding.machineTicker == null"));
        assertTrue(first.contains("ticker.tick(tickState)"));
        assertTrue(first.contains("return fluidTank(spec)"));
        assertTrue(first.contains("binding.useHandler == null && !binding.spec.tanks().isEmpty()"));
        assertTrue(first.contains("showFluidStatus(owner, player, \"Tank contents\")"));
        assertTrue(first.contains("return pushToNeighbour(spec, face, maximum, simulate)"));
        assertTrue(first.contains("if (!level.getChunkSource().hasChunk(neighbourPos.getX() >> 4, neighbourPos.getZ() >> 4)) return 0"));
        assertTrue(first.contains("!source.port(face).allowsDrain()"));
        assertTrue(first.contains("!target.port(inputFace).allowsFill()"));
        assertTrue(first.contains("setChanged(); neighbour.setChanged()"));
        assertTrue(first.contains("storage.tank(spec), this::requireTankAccess, this::setChanged"));
        assertTrue(first.contains("isRemoved() || level.getBlockEntity(worldPosition) != this"));
        assertTrue(first.contains("if (tickingFailed) return"));
        assertTrue(first.contains("tickingFailed = true"));
        assertTrue(first.contains("level.getServer().isSameThread()"));
        assertTrue(first.contains("new BlockLocation(ResourceId.parse(level.dimension().location().toString())"));
        assertTrue(first.contains("pos.getX(), pos.getY(), pos.getZ()"));
        assertTrue(first.contains("player.isSpectator() || binding.useHandler == null"));
        assertTrue(first.contains("public void startOpen(Player user)"));
        assertTrue(first.contains("public void stopOpen(Player user)"));
        assertTrue(first.contains("updateContainerOpenState(true)"));
        assertTrue(first.contains("viewer.containerMenu instanceof net.minecraft.world.inventory.ChestMenu menu"));
        Path menu = directory.resolve("uk/co/enderfall/sdk/runtime/fabric/v1_21_4/FabricPersistentWorkbenchMenu.java");
        String menuSource = Files.readString(menu);
        String processor = Files.readString(menu.getParent().resolve("FabricTimedWorkbenchProcessor.java"));
        String timedMenu = Files.readString(menu.getParent().resolve("FabricTimedWorkbenchMenu.java"));
        assertTrue(timedMenu.contains("addDataSlots(progress)"));
        assertTrue(timedMenu.contains("new SimpleContainerData(2)"));
        assertTrue(timedMenu.contains("owner.processingStatus().code()"));
        assertTrue(processor.contains("PortableMachineStatus.REMAINDER_BLOCKED : PortableMachineStatus.OUTPUT_BLOCKED"));
        String timedClient = Files.readString(menu.getParent().resolve("FabricTimedWorkbenchClient.java"));
        assertTrue(timedClient.contains("enderfall_sdk.machine.output_blocked"));
        assertTrue(timedClient.contains("menu.progressPermille() / 10"));
        assertTrue(timedMenu.contains("owner.removeInventoryListener(listener)"));
        assertFalse(timedMenu.contains("clearContainer("));
        assertFalse(timedMenu.contains("completeCraft("));
        assertTrue(timedMenu.contains("owner == null ? new SimpleContainer(inputs + 1) : owner.inventory()"));
        assertTrue(processor.contains("level.recipeAccess().getRecipeFor"));
        assertTrue(processor.contains("ItemStack.isSameItemSameComponents"));
        assertTrue(processor.contains("getCraftingRemainder()"));
        assertTrue(processor.contains("owner.commitProcessing(completed, Map.of(), step.next())"));
        assertTrue(processor.contains("PortableProcessingCycle.advance(previous, job, fits)"));
        assertTrue(menuSource.contains("inputs = owner.inventory()"));
        assertTrue(menuSource.contains("owner.addInventoryListener(inputListener)"));
        assertTrue(menuSource.contains("owner.removeInventoryListener(inputListener)"));
        assertTrue(menuSource.contains("return inputs.stillValid(player)"));
        assertFalse(menuSource.contains("clearContainer(player, inputs)"));
        assertFalse(menuSource.contains("inputs = new SimpleContainer"));
        assertTrue(menuSource.contains("level.getServer().isSameThread()"));
        assertTrue(menuSource.contains("level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)"));
        assertTrue(menuSource.contains("Persistent workbench owner, schema, or player reach mismatch"));
        assertTrue(menuSource.contains("player.openMenu(new net.minecraft.world.SimpleMenuProvider("));
        assertTrue(menuSource.contains("block.onUse((player, event) ->"));
        assertTrue(menuSource.contains("openAt(serverPlayer, binding, event.blockLocation().orElseThrow())"));
        String platform = Files.readString(menu.getParent().resolve("FabricPersistentPlatformAdapter.java"));
        assertTrue(platform.contains("final class FabricPersistentPlatformAdapter implements PlatformAdapter"));
        assertTrue(platform.contains("public boolean supportsPersistentWorkbenches() { return true; }"));
        assertTrue(platform.contains("public boolean supportsStorageContainers() { return true; }"));
        assertTrue(platform.contains("public void registerStorageContainer("));
        assertTrue(platform.contains("new net.minecraft.world.inventory.ChestMenu("));
        assertTrue(platform.contains("new BlockItem(block, itemProperties)"));
        assertTrue(platform.contains("FabricPersistentWorkbenchMenu.bindBlock(owner, workbenches.get("));
        assertTrue(platform.contains("FabricPersistentWorkbenchMenu.openAt(requireOnlinePlayer(playerId)"));
        assertTrue(platform.contains("blocks.put(id, block)"));
        assertTrue(platform.contains("items.put(id, item)"));
        assertTrue(platform.contains("boundPersistentBlocks.contains(blockId)"));
        String bootstrap = Files.readString(menu.getParent().resolve("FabricPersistentPreviewBootstrap.java"));
        assertTrue(bootstrap.contains("new FabricPersistentPlatformAdapter(modId)"));
        assertTrue(bootstrap.indexOf("INITIALIZED.add(modId)") < bootstrap.indexOf("RuntimeModBootstrap.initialize("));
        assertTrue(bootstrap.contains("adapter.attach(context)"));
        BlockEntityPreviewMain.main(new String[] {"1.21.4-fabric", directory.toString()});
        assertEquals(platform, Files.readString(menu.getParent().resolve("FabricPersistentPlatformAdapter.java")));
        assertEquals(bootstrap, Files.readString(menu.getParent().resolve("FabricPersistentPreviewBootstrap.java")));
    }

    @Test void unsupportedTargetsFailBeforeWritingAnySource() {
        assertThrows(IllegalArgumentException.class, () -> BlockEntityPreviewMain.main(
                new String[] {"27.1-fabric", directory.resolve("unsupported").toString()}));
        assertFalse(Files.exists(directory.resolve("unsupported")));
    }
}
