package uk.co.enderfall.sdk.bridge;

/** Authored native operations for the first block-entity persistence slice. */
final class BlockEntityPreviewSources {
    private BlockEntityPreviewSources() { }

    static String emit(String target) {
        BlockEntityNativePolicy policy = BlockEntityNativePolicy.require(target);
        String source = """
                package uk.co.enderfall.sdk.runtime.blockentity.nativebridge;

                import java.io.*;
                import java.util.Objects;
                ${BUILDER_IMPORT}
                import net.minecraft.core.BlockPos;
                import net.minecraft.core.HolderLookup;
                import net.minecraft.core.Registry;
                import net.minecraft.core.registries.BuiltInRegistries;
                import net.minecraft.core.registries.Registries;
                import net.minecraft.nbt.*;
                import net.minecraft.resources.ResourceKey;
                import net.minecraft.resources.ResourceLocation;
                import net.minecraft.world.item.ItemStack;
                import net.minecraft.world.SimpleContainer;
                import net.minecraft.world.Containers;
                import net.minecraft.world.entity.player.Player;
                import net.minecraft.world.level.Level;
                import net.minecraft.world.level.block.Block;
                import net.minecraft.world.level.block.EntityBlock;
                import net.minecraft.world.level.block.entity.BlockEntity;
                import net.minecraft.world.level.block.entity.BlockEntityType;
                import net.minecraft.world.level.block.state.BlockBehaviour;
                import net.minecraft.world.level.block.state.BlockState;
                import uk.co.enderfall.sdk.api.blockentity.BlockEntityInt;
                import uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec;
                import uk.co.enderfall.sdk.api.blockentity.BlockLocation;
                import uk.co.enderfall.sdk.api.event.InteractionEvent;
                import uk.co.enderfall.sdk.api.ResourceId;
                import uk.co.enderfall.sdk.runtime.blockentity.BlockEntityStackCodec;
                import uk.co.enderfall.sdk.runtime.blockentity.PortableBlockEntityStorage;
                import uk.co.enderfall.sdk.runtime.blockentity.PortableProcessingCycle;
                import uk.co.enderfall.sdk.runtime.blockentity.ProcessingStateCodec;

                /** Development-only native binding. Not shipped until container/drop integration is complete. */
                public final class StoredBlockEntity extends BlockEntity implements net.minecraft.world.WorldlyContainer {
                    private static final String SAVE_KEY = "enderfall_storage";
                    private static final String PROCESS_KEY = "enderfall_processing";
                    private PortableProcessingCycle.State processing = PortableProcessingCycle.State.idle();
                    private uk.co.enderfall.sdk.runtime.blockentity.PortableMachineStatus processingStatus =
                            uk.co.enderfall.sdk.runtime.blockentity.PortableMachineStatus.IDLE;
                    private Runnable notifyInventoryCommit;
                    private final java.util.List<Runnable> inventoryListeners = new java.util.ArrayList<>();
                    private final java.util.Set<java.util.UUID> containerViewers = new java.util.HashSet<>();
                    private long lastViewerCheck;
                    public void addInventoryListener(Runnable listener) { requireServer(); inventoryListeners.add(Objects.requireNonNull(listener)); }
                    public void removeInventoryListener(Runnable listener) { inventoryListeners.remove(listener); }
                    private void notifyInventoryListeners() {
                        ${RENDER_NOTIFY}
                        for (Runnable listener : java.util.List.copyOf(inventoryListeners)) {
                            try { listener.run(); }
                            catch (RuntimeException failure) {
                                System.getLogger("enderfall_sdk").log(System.Logger.Level.ERROR,
                                        "Inventory listener failed for " + definition.block().id(), failure);
                            }
                        }
                    }
                    private final PortableBlockEntityStorage<ItemStack> storage;
                    private final BlockEntitySpec definition;
                    private final uk.co.enderfall.sdk.runtime.blockentity.NamedAnimationController animations;
                    private int animationViewers;
                    public void menuAnimationOpened() {
                        requireServer();
                        if (animationViewers++ == 0) definition.menuOpenAnimation().ifPresent(name -> animations.play(name, level.getGameTime()));
                    }
                    public void menuAnimationClosed() {
                        if (level == null || level.isClientSide || isRemoved()) return;
                        requireServer();
                        if (animationViewers > 0 && --animationViewers == 0) definition.menuCloseAnimation().ifPresent(name -> animations.play(name, level.getGameTime()));
                    }
                    @Override public void startOpen(${CONTAINER_USER_TYPE} user) {
                        ${CONTAINER_PLAYER_CAST}
                        if (level == null || level.isClientSide || player.isSpectator() || binding.storageContainer == null) return;
                        requireServer();
                        if (containerViewers.add(player.getUUID()) && containerViewers.size() == 1) {
                            updateContainerOpenState(true);
                            playContainerSound(true);
                            menuAnimationOpened();
                            level.scheduleTick(worldPosition, getBlockState().getBlock(), 20);
                        }
                    }
                    @Override public void stopOpen(${CONTAINER_USER_TYPE} user) {
                        ${CONTAINER_PLAYER_CAST}
                        if (level == null || level.isClientSide || binding.storageContainer == null) return;
                        requireServer();
                        if (containerViewers.remove(player.getUUID()) && containerViewers.isEmpty()) {
                            updateContainerOpenState(false);
                            playContainerSound(false);
                            menuAnimationClosed();
                        }
                    }
                    private void updateContainerOpenState(boolean open) {
                        var property = binding.storageContainer.spec().openProperty();
                        if (property.isEmpty()) return;
                        BlockState current = getBlockState();
                        if (!(current.getBlock() instanceof PortableShapeBlock block)) return;
                        var portable = block.portableState(current);
                        if (portable.get(property.orElseThrow()) == open) return;
                        level.setBlock(worldPosition, block.withPortableState(current,
                                portable.with(property.orElseThrow(), open)), Block.UPDATE_ALL);
                    }
                    @SuppressWarnings("deprecation")
                    private void playContainerSound(boolean opening) {
                        var sounds = binding.storageContainer.spec().sounds();
                        if (!sounds.enabled()) return;
                        var id = opening ? sounds.open() : sounds.close();
                        var sound = ${SOUND_LOOKUP};
                        if (sound == null) throw new IllegalStateException("Unknown container sound: " + id);
                        level.playSound(null, worldPosition, sound,
                                net.minecraft.sounds.SoundSource.BLOCKS, sounds.volume(), sounds.pitch());
                    }
                    private void reconcileContainerViewers() {
                        if (containerViewers.isEmpty() || level.getGameTime() - lastViewerCheck < 20) return;
                        lastViewerCheck = level.getGameTime();
                        containerViewers.removeIf(id -> {
                            var viewer = level.getServer().getPlayerList().getPlayer(id);
                            return viewer == null || !viewer.isAlive() || viewer.isSpectator()
                                    || !(viewer.containerMenu instanceof net.minecraft.world.inventory.ChestMenu menu)
                                    || menu.getContainer() != StoredBlockEntity.this;
                        });
                        if (containerViewers.isEmpty()) {
                            updateContainerOpenState(false);
                            playContainerSound(false);
                            menuAnimationClosed();
                        }
                    }
                    private boolean hasContainerViewers() { return !containerViewers.isEmpty(); }
                    private final Binding binding;
                    private final SimpleContainer inventory;
                    private boolean restoringInventory;
                    private boolean tickingFailed;
                    private final uk.co.enderfall.sdk.api.blockentity.BlockEntityState tickState =
                            new uk.co.enderfall.sdk.api.blockentity.BlockEntityState() {
                                public void playAnimation(String name) { requireServer(); animations.play(name, level.getGameTime()); }
                                public void stopAnimation() { requireServer(); animations.stop(level.getGameTime()); }
                                public int get(BlockEntityInt field) {
                                    requireServer();
                                    return value(field);
                                }
                                public void set(BlockEntityInt field, int value) {
                                    StoredBlockEntity.this.value(field, value);
                                }
                                public uk.co.enderfall.sdk.api.fluid.FluidTank tank(uk.co.enderfall.sdk.api.fluid.FluidTankSpec spec) {
                                    return fluidTank(spec);
                                }
                                public long pushFluid(uk.co.enderfall.sdk.api.fluid.FluidTankSpec spec,
                                        uk.co.enderfall.sdk.api.fluid.FluidFace face, long maximum, boolean simulate) {
                                    return pushToNeighbour(spec, face, maximum, simulate);
                                }
                            };
                    private HolderLookup.Provider serializationRegistries;

                    private StoredBlockEntity(Binding binding, BlockPos pos, BlockState state) {
                        super(Objects.requireNonNull(binding.type, "Block entity type not registered"), pos, state);
                        definition = binding.spec;
                        animations = new uk.co.enderfall.sdk.runtime.blockentity.NamedAnimationController(definition.animations(), this::notifyRenderSnapshot);
                        this.binding = binding;
                        storage = new PortableBlockEntityStorage<>(binding.spec, new NativeStacks());
                        inventory = new SimpleContainer(binding.spec.inventorySlots()) {
                            { notifyInventoryCommit = () -> { super.setChanged(); notifyInventoryListeners(); }; }
                            @Override
                            public void setChanged() {
                                if (restoringInventory) return;
                                requireServer();
                                try {
                                    storage.replaceInventory(containerStacks(this));
                                } catch (RuntimeException failure) {
                                    restoringInventory = true;
                                    try {
                                        for (int slot = 0; slot < storage.size(); slot++) super.setItem(slot, storage.stack(slot));
                                    } finally { restoringInventory = false; }
                                    throw failure;
                                }
                                StoredBlockEntity.this.setChanged();
                                // Notify open menus only after the new snapshot is committed.
                                super.setChanged();
                                notifyInventoryListeners();
                            }

                            @Override
                            public void setItem(int slot, ItemStack stack) {
                                if (restoringInventory) { super.setItem(slot, stack); return; }
                                requireServer();
                                Objects.checkIndex(slot, getContainerSize());
                                if (!stack.isEmpty() && stack.getCount() > Math.min(getMaxStackSize(), stack.getMaxStackSize())) {
                                    throw new IllegalArgumentException("Stack exceeds native slot capacity");
                                }
                                new NativeStacks().encode(stack);
                                super.setItem(slot, stack.copy());
                            }

                            @Override
                            public boolean stillValid(Player player) {
                                return !isRemoved() && level != null && player.level() == level
                                        && level.getBlockEntity(worldPosition) == StoredBlockEntity.this
                                        && player.distanceToSqr(worldPosition.getX() + 0.5,
                                                worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
                            }

                            @Override
                            public ItemStack removeItem(int slot, int amount) {
                                requireServer();
                                Objects.checkIndex(slot, getContainerSize());
                                return amount <= 0 ? ItemStack.EMPTY : super.removeItem(slot, amount);
                            }

                            @Override
                            public ItemStack removeItemNoUpdate(int slot) {
                                requireServer();
                                Objects.checkIndex(slot, getContainerSize());
                                ItemStack removed = super.removeItemNoUpdate(slot);
                                if (!removed.isEmpty()) setChanged();
                                return removed;
                            }

                            @Override
                            public void clearContent() {
                                requireServer();
                                super.clearContent();
                                setChanged();
                            }
                        };
                    }

                ${REGISTRATION}

                    public int inventorySize() { return storage.size(); }
                    private static java.util.List<ItemStack> containerStacks(net.minecraft.world.Container container) {
                        var stacks = new java.util.ArrayList<ItemStack>(container.getContainerSize());
                        for (int slot = 0; slot < container.getContainerSize(); slot++) stacks.add(container.getItem(slot));
                        return stacks;
                    }
                    @Override public int getContainerSize() { return inventory.getContainerSize(); }
                    @Override public boolean isEmpty() { return inventory.isEmpty(); }
                    @Override public ItemStack getItem(int slot) { return inventory.getItem(slot); }
                    @Override public ItemStack removeItem(int slot, int amount) { return inventory.removeItem(slot, amount); }
                    @Override public ItemStack removeItemNoUpdate(int slot) { return inventory.removeItemNoUpdate(slot); }
                    @Override public void setItem(int slot, ItemStack stack) { inventory.setItem(slot, stack); }
                    @Override public void clearContent() { inventory.clearContent(); }
                    @Override public boolean stillValid(Player player) { return inventory.stillValid(player); }
                    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
                        return binding.inventoryPorts != null && binding.inventoryPorts.canInsertFromAnyFace(slot)
                                || binding.ports != null && binding.ports.isInput(slot);
                    }
                    @Override public boolean canTakeItem(net.minecraft.world.Container destination, int slot, ItemStack stack) {
                        return binding.inventoryPorts != null && binding.inventoryPorts.canExtractFromAnyFace(slot)
                                || binding.ports != null && binding.ports.isOutput(slot);
                    }
                    private static uk.co.enderfall.sdk.runtime.blockentity.PortableMachinePorts.Face machinePortFace(net.minecraft.core.Direction face) {
                        return face == null ? null : uk.co.enderfall.sdk.runtime.blockentity.PortableMachinePorts.Face.valueOf(face.name());
                    }
                    private static uk.co.enderfall.sdk.api.block.BlockDirection inventoryPortFace(net.minecraft.core.Direction face) {
                        return face == null ? null : uk.co.enderfall.sdk.api.block.BlockDirection.valueOf(face.name());
                    }
                    @Override public int[] getSlotsForFace(net.minecraft.core.Direction face) {
                        if (face == null) return new int[0];
                        if (binding.inventoryPorts != null) return binding.inventoryPorts.slots(inventoryPortFace(face));
                        return binding.ports == null ? new int[0] : binding.ports.slots(machinePortFace(face));
                    }
                    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction face) {
                        return binding.inventoryPorts != null && face != null
                                && binding.inventoryPorts.canInsert(inventoryPortFace(face), slot)
                                || binding.ports != null && binding.ports.canInsert(slot, machinePortFace(face));
                    }
                    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction face) {
                        return binding.inventoryPorts != null && face != null
                                && binding.inventoryPorts.canExtract(inventoryPortFace(face), slot)
                                || binding.ports != null && binding.ports.canExtract(slot, machinePortFace(face));
                    }
                    public BlockEntitySpec definition() { return definition; }
                    public ItemStack stack(int slot) { return inventory.getItem(slot).copy(); }
                    /** Internal server menu binding. Menus must remove listeners on close, not clear this inventory. */
                    public SimpleContainer inventory() {
                        requireServer();
                        return inventory;
                    }
                    public int value(BlockEntityInt field) { return storage.get(field); }

                    public void stack(int slot, ItemStack stack) {
                        requireServer();
                        inventory.setItem(slot, stack);
                    }

                    public void value(BlockEntityInt field, int value) {
                        requireServer();
                        long before = storage.revision();
                        storage.set(field, value);
                        if (before != storage.revision()) setChanged();
                    }

                    public uk.co.enderfall.sdk.api.fluid.FluidTank fluidTank(uk.co.enderfall.sdk.api.fluid.FluidTankSpec spec) {
                        requireTankAccess();
                        return new uk.co.enderfall.sdk.runtime.fluid.GuardedFluidTank(
                                storage.tank(spec), this::requireTankAccess, this::setChanged);
                    }

                    private void requireTankAccess() {
                        requireServer();
                        if (isRemoved() || level.getBlockEntity(worldPosition) != this) {
                            throw new IllegalStateException("Fluid tank owner is no longer attached");
                        }
                    }

                    /** Internal world-facing connection for future loader fluid capabilities. */
                    public long pushToNeighbour(uk.co.enderfall.sdk.api.fluid.FluidTankSpec source,
                            uk.co.enderfall.sdk.api.fluid.FluidFace face, long maximum, boolean simulate) {
                        requireTankAccess();
                        storage.tank(source);
                        Objects.requireNonNull(face, "face");
                        if (maximum < 0) throw new IllegalArgumentException("Negative fluid transfer limit");
                        if (maximum == 0 || !source.port(face).allowsDrain()) return 0;
                        var direction = net.minecraft.core.Direction.valueOf(face.name());
                        var neighbourPos = worldPosition.relative(direction);
                        if (!level.getChunkSource().hasChunk(neighbourPos.getX() >> 4, neighbourPos.getZ() >> 4)) return 0;
                        if (!(level.getBlockEntity(neighbourPos) instanceof StoredBlockEntity neighbour)) return 0;
                        neighbour.requireTankAccess();
                        var inputFace = uk.co.enderfall.sdk.api.fluid.FluidFace.valueOf(direction.getOpposite().name());
                        for (var target : neighbour.definition.tanks().values()) {
                            if (!target.port(inputFace).allowsFill()) continue;
                            long moved = storage.transferTank(source, neighbour.storage, target, maximum, simulate);
                            if (moved > 0) {
                                if (!simulate) { setChanged(); neighbour.setChanged(); }
                                return moved;
                            }
                        }
                        return 0;
                    }

                    /** Internal world-facing connection for future loader fluid capabilities. */
                    public uk.co.enderfall.sdk.api.fluid.FluidTank fluidPort(
                            uk.co.enderfall.sdk.api.fluid.FluidTankSpec spec, net.minecraft.core.Direction face) {
                        Objects.requireNonNull(face, "face");
                        var portableFace = uk.co.enderfall.sdk.api.fluid.FluidFace.valueOf(face.name());
                        return new uk.co.enderfall.sdk.runtime.fluid.FluidPortView(fluidTank(spec), spec.port(portableFace));
                    }

                    private void requireServer() {
                        if (level == null || level.isClientSide || isRemoved()) {
                            throw new IllegalStateException("Block entity mutation requires an attached server level");
                        }
                        if (!level.getServer().isSameThread()) {
                            throw new IllegalStateException("Block entity access requires the owning server thread");
                        }
                    }

                    public PortableProcessingCycle.State processingState() {
                        requireServer();
                        return processing;
                    }

                    public uk.co.enderfall.sdk.runtime.blockentity.PortableMachineStatus processingStatus() {
                        requireServer();
                        return processingStatus;
                    }

                    /** Derived feedback, recomputed by ticks rather than persisted in world saves. */
                    public void processingStatus(uk.co.enderfall.sdk.runtime.blockentity.PortableMachineStatus value) {
                        requireServer();
                        processingStatus = Objects.requireNonNull(value, "status");
                    }

                    /** Server-only transaction; listeners see the committed slots and processing state together. */
                    public void commitProcessing(java.util.List<ItemStack> replacement,
                            java.util.Map<BlockEntityInt, Integer> fields, PortableProcessingCycle.State next) {
                        commitMachineProcessing(replacement, fields, next, java.util.Map.of());
                    }
                    public void commitMachineProcessing(java.util.List<ItemStack> replacement,
                            java.util.Map<BlockEntityInt, Integer> fields, PortableProcessingCycle.State next,
                            java.util.Map<String, java.util.Optional<uk.co.enderfall.sdk.api.fluid.FluidVolume>> tanks) {
                        requireServer();
                        // Prepare every potentially failing copy/validation before changing either owner.
                        ProcessingStateCodec.encode(next);
                        if (replacement.size() != inventorySize()) throw new IllegalArgumentException("Processing inventory size mismatch");
                        for (ItemStack stack : replacement) {
                            if (!stack.isEmpty() && stack.getCount() > Math.min(inventory.getMaxStackSize(), stack.getMaxStackSize())) {
                                throw new IllegalArgumentException("Processing stack exceeds slot capacity");
                            }
                        }
                        var owned = replacement.stream().map(ItemStack::copy).toList();
                        storage.replaceInventoryFieldsAndTanks(owned, fields, tanks);
                        restoringInventory = true;
                        try {
                            for (int slot = 0; slot < owned.size(); slot++) inventory.setItem(slot, owned.get(slot));
                        } finally { restoringInventory = false; }
                        processing = next;
                        setChanged();
                        try {
                            notifyInventoryCommit.run();
                        } catch (RuntimeException failure) {
                            // The transaction succeeded; do not report a failed craft that could be retried.
                            System.getLogger("enderfall_sdk").log(System.Logger.Level.ERROR,
                                    "Post-commit viewer notification failed for " + definition.block().id()
                                            + " on ${TARGET} at " + worldPosition, failure);
                        }
                    }

                    private void serverTick() {
                        requireServer();
                        if (tickingFailed) return;
                        try {
                            if (binding.machineTicker != null) binding.machineTicker.accept(this);
                            definition.serverTicker().ifPresent(ticker -> ticker.tick(tickState));
                        } catch (RuntimeException failure) {
                            tickingFailed = true;
                            processingStatus = uk.co.enderfall.sdk.runtime.blockentity.PortableMachineStatus.FAULT;
                            System.getLogger("enderfall_sdk").log(System.Logger.Level.ERROR,
                                    "Disabled block ticker for " + definition.block().id()
                                            + " on ${TARGET} at " + level.dimension().location()
                                            + " " + worldPosition + "; reload required", failure);
                        }
                    }

                    @Override
                    protected void saveAdditional(${SAVE_ARGS}) {
                        super.saveAdditional(${SAVE_SUPER_ARGS});
                        serializationRegistries = ${REGISTRY_CONTEXT};
                        try {
                            // Native slots can mutate live stacks before issuing their change notification.
                            storage.replaceInventory(containerStacks(inventory));
                            tag.putByteArray(SAVE_KEY, storage.save());
                            tag.putByteArray(PROCESS_KEY, ProcessingStateCodec.encode(processing));
                            // Building chunk NBT is not acknowledgement that the world file reached disk.
                        } finally {
                            serializationRegistries = null;
                        }
                    }

                    @Override
                    ${LOAD_SIGNATURE} {
                        ${LOAD_SUPER};
                        ${RENDER_LOAD}
                        if (!tag.contains(SAVE_KEY)) {
                            if (tag.contains(PROCESS_KEY)) throw new IllegalArgumentException("Processing state has no inventory snapshot");
                            return; // Newly placed block, no saved inventory yet.
                        }
                        if (!tag.contains(SAVE_KEY, Tag.TAG_BYTE_ARRAY)) {
                            throw new IllegalArgumentException("Wrong EnderFall block-entity storage tag type");
                        }
                        serializationRegistries = ${REGISTRY_CONTEXT};
                        try {
                            PortableProcessingCycle.State restoredProcessing = PortableProcessingCycle.State.idle();
                            if (tag.contains(PROCESS_KEY)) {
                                if (!tag.contains(PROCESS_KEY, Tag.TAG_BYTE_ARRAY)) {
                                    throw new IllegalArgumentException("Wrong EnderFall processing tag type");
                                }
                                restoredProcessing = ProcessingStateCodec.decode(tag.getByteArray(PROCESS_KEY));
                            }
                            storage.restore(tag.getByteArray(SAVE_KEY));
                            processing = restoredProcessing;
                            restoringInventory = true;
                            for (int slot = 0; slot < storage.size(); slot++) {
                                inventory.setItem(slot, storage.stack(slot));
                            }
                        } finally {
                            restoringInventory = false;
                            serializationRegistries = null;
                        }
                    }

                    ${RENDER_SNAPSHOT}
                    private HolderLookup.Provider registries() {
                        // Native loading can happen before setLevel; use the supplied load/save lookup.
                        if (serializationRegistries != null) return serializationRegistries;
                        if (level != null) return level.registryAccess();
                        throw new IllegalStateException("Native item serialization needs a registry lookup");
                    }

                    private final class NativeStacks implements BlockEntityStackCodec<ItemStack> {
                        public ItemStack empty() { return ItemStack.EMPTY; }
                        public ItemStack copy(ItemStack stack) { return stack.copy(); }

                        public byte[] encode(ItemStack stack) {
                            if (stack.isEmpty()) return new byte[0];
                            validate(stack);
                            Tag encoded = ${STACK_ENCODE};
                            if (!(encoded instanceof CompoundTag compound)) {
                                throw new IllegalArgumentException("Item codec did not produce a compound");
                            }
                            try {
                                ByteArrayOutputStream bytes = new BoundedItemOutput();
                                NbtIo.write(compound, new DataOutputStream(bytes));
                                return bytes.toByteArray();
                            } catch (IOException failure) {
                                throw new IllegalArgumentException("Cannot encode native stack", failure);
                            }
                        }

                        public ItemStack decode(byte[] encoded) {
                            if (encoded.length == 0) return ItemStack.EMPTY;
                            if (encoded.length > PortableBlockEntityStorage.MAXIMUM_STACK_BYTES) {
                                throw new IllegalArgumentException("Native item data exceeds byte limit");
                            }
                            try {
                                DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
                                CompoundTag tag = NbtIo.read(input, ${NBT_ACCOUNTER});
                                if (input.available() != 0) throw new IllegalArgumentException("Trailing native item data");
                                ItemStack stack = ${STACK_DECODE};
                                validate(stack);
                                return stack;
                            } catch (IOException failure) {
                                throw new IllegalArgumentException("Cannot decode native stack", failure);
                            }
                        }

                        private void validate(ItemStack stack) {
                            if (stack.isEmpty() || stack.getCount() < 1 || stack.getCount() > Math.min(inventory.getMaxStackSize(), stack.getMaxStackSize())) {
                                throw new IllegalArgumentException("Invalid native item stack count");
                            }
                        }
                    }

                    private static final class BoundedItemOutput extends ByteArrayOutputStream {
                        @Override
                        public synchronized void write(int value) {
                            requireCapacity(1);
                            super.write(value);
                        }
                        @Override
                        public synchronized void write(byte[] bytes, int offset, int length) {
                            Objects.checkFromIndexSize(offset, length, bytes.length);
                            requireCapacity(length);
                            super.write(bytes, offset, length);
                        }
                        private void requireCapacity(int added) {
                            if (added > PortableBlockEntityStorage.MAXIMUM_STACK_BYTES - count) {
                                throw new IllegalArgumentException("Native item data exceeds byte limit");
                            }
                        }
                    }

                    public static final class Binding {
                        ${BLOCK_HANDLE_FIELD}
                        private final BlockEntitySpec spec;
                        private final uk.co.enderfall.sdk.api.registry.BlockSpec blockSpec;
                        private Block block;
                        private BlockEntityType<StoredBlockEntity> type;
                        private java.util.function.BiConsumer<Player, InteractionEvent> useHandler;
                        private java.util.function.Consumer<StoredBlockEntity> machineTicker;
                        private uk.co.enderfall.sdk.runtime.blockentity.PortableMachinePorts ports;
                        private uk.co.enderfall.sdk.api.blockentity.InventoryAccessSpec inventoryPorts;
                        private uk.co.enderfall.sdk.runtime.PortableStorageContainerDefinition storageContainer;
                        private Binding(BlockEntitySpec spec, uk.co.enderfall.sdk.api.registry.BlockSpec blockSpec) {
                            this.spec = spec;
                            this.blockSpec = java.util.Objects.requireNonNull(blockSpec, "blockSpec");
                            this.inventoryPorts = spec.inventoryAccess().orElse(null);
                        }
                        public Block block() { return block; }
                        public BlockEntityType<StoredBlockEntity> type() { return type; }
                        public BlockEntitySpec definition() { return spec; }
                        /** Enable the reviewed positional automation policy only for a timed machine. */
                        public void enableMachinePorts(int inputs) {
                            if (ports != null || inventoryPorts != null || spec.inventorySlots() != inputs + 1) {
                                throw new IllegalArgumentException("Machine port binding is duplicate or has mismatched slots");
                            }
                            ports = new uk.co.enderfall.sdk.runtime.blockentity.PortableMachinePorts(inputs);
                        }
                        public void bindStorageContainer(uk.co.enderfall.sdk.runtime.PortableStorageContainerDefinition definition) {
                            Objects.requireNonNull(definition, "definition");
                            if (storageContainer != null || ports != null || useHandler != null
                                    || !definition.spec().storage().equals(spec)) {
                                throw new IllegalArgumentException("Storage container is duplicate, mismatched, or conflicts with another menu");
                            }
                            definition.spec().openProperty().ifPresent(property -> {
                                if (!blockSpec.states().properties().contains(property)) {
                                    throw new IllegalArgumentException("Container open property is not declared by the block");
                                }
                            });
                            storageContainer = definition;
                            if (inventoryPorts == null) {
                                inventoryPorts = uk.co.enderfall.sdk.api.blockentity.InventoryAccessSpec.allFaces(spec.inventorySlots());
                            }
                        }
                        /** Internal preview processing hook; install once before blocks are created. */
                        public void onMachineTick(java.util.function.Consumer<StoredBlockEntity> ticker) {
                            Objects.requireNonNull(ticker, "ticker");
                            if (machineTicker != null) throw new IllegalStateException("Persistent block already has a machine ticker");
                            machineTicker = ticker;
                        }
                        /** Bind once during registration, before this block can be used in a world. */
                        public void onUse(java.util.function.BiConsumer<Player, InteractionEvent> handler) {
                            Objects.requireNonNull(handler, "handler");
                            if (useHandler != null) throw new IllegalStateException("Persistent block already has a use handler");
                            useHandler = handler;
                        }
                    }

                    private static StoredBlock createStoredBlock(BlockBehaviour.Properties properties, Binding binding) {
                        return PortableShapeBlock.construct(binding.blockSpec, () -> {
                            var block = binding.blockSpec.sixWayFacing() ? new SixWayStoredBlock(properties, binding)
                                    : binding.blockSpec.horizontalFacing() ? new DirectionalStoredBlock(properties, binding)
                                    : binding.blockSpec.axisFacing() ? new AxisStoredBlock(properties, binding) : new StoredBlock(properties, binding);
                            block.initializeFacing();
                            return block;
                        });
                    }
                    private static final class DirectionalStoredBlock extends StoredBlock implements PortableShapeBlock.Directional {
                        private DirectionalStoredBlock(BlockBehaviour.Properties properties, Binding binding) { super(properties, binding); }
                    }
                    private static final class SixWayStoredBlock extends StoredBlock implements PortableShapeBlock.SixWayDirectional {
                        private SixWayStoredBlock(BlockBehaviour.Properties properties, Binding binding) { super(properties, binding); }
                    }
                    private static final class AxisStoredBlock extends StoredBlock implements PortableShapeBlock.AxisOriented {
                        private AxisStoredBlock(BlockBehaviour.Properties properties, Binding binding) { super(properties, binding); }
                    }
                    private static class StoredBlock extends PortableShapeBlock implements EntityBlock {
                        private final Binding binding;
                        private StoredBlock(BlockBehaviour.Properties properties, Binding binding) {
                            super(properties, binding.blockSpec);
                            this.binding = binding;
                        }
                        @Override
                        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
                            return new StoredBlockEntity(binding, pos, state);
                        }

                        @Override
                        public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T>
                                getTicker(Level level, BlockState state, BlockEntityType<T> type) {
                            if (level.isClientSide || type != binding.type
                                    || (binding.spec.serverTicker().isEmpty() && binding.machineTicker == null)) {
                                return null;
                            }
                            return (tickLevel, pos, tickState, entity) -> {
                                if (entity instanceof StoredBlockEntity stored && !stored.isRemoved()) stored.serverTick();
                            };
                        }

                        @Override @SuppressWarnings("deprecation")
                        public void tick(BlockState state, net.minecraft.server.level.ServerLevel level,
                                BlockPos pos, net.minecraft.util.RandomSource random) {
                            super.tick(state, level, pos, random);
                            if (level.getBlockEntity(pos) instanceof StoredBlockEntity stored
                                    && stored.binding.storageContainer != null) {
                                stored.reconcileContainerViewers();
                                if (stored.hasContainerViewers()) level.scheduleTick(pos, this, 20);
                            }
                        }

                        @Override
                        ${LEGACY_OVERRIDE_ANNOTATION}${BLOCK_USE_SIGNATURE} {
                            ${LEGACY_FLUID_BUCKET}
                            if (!player.isSpectator() && binding.useHandler == null && !binding.spec.tanks().isEmpty()) {
                                if (!level.isClientSide && level.getBlockEntity(pos) instanceof StoredBlockEntity owner) {
                                    showFluidStatus(owner, player, "Tank contents");
                                }
                                return net.minecraft.world.InteractionResult.SUCCESS;
                            }
                            if (player.isSpectator() || binding.useHandler == null) {
                                return net.minecraft.world.InteractionResult.PASS;
                            }
                            InteractionEvent event = new InteractionEvent(InteractionEvent.Kind.USE_BLOCK,
                                    level.isClientSide ? InteractionEvent.Side.CLIENT : InteractionEvent.Side.SERVER,
                                    player.getUUID(), binding.spec.block().id(),
                                    new BlockLocation(ResourceId.parse(level.dimension().location().toString()),
                                            pos.getX(), pos.getY(), pos.getZ()));
                            binding.useHandler.accept(player, event);
                            return event.cancelled() ? net.minecraft.world.InteractionResult.FAIL
                                    : event.handled() ? net.minecraft.world.InteractionResult.SUCCESS
                                    : net.minecraft.world.InteractionResult.PASS;
                        }

                        ${FLUID_BUCKET_HELPER}
                        ${FLUID_BUCKET_HOOK}
                        ${BLOCK_REMOVAL}
                    }
                }
                """.replace("${REGISTRATION}", BlockEntityRegistrationSources.emit(policy))
                .replace("${BLOCK_PROPERTIES}", policy.blockProperties()).replace("${TARGET}", policy.target())
                .replace("${NATIVE_ID}", policy.nativeId())
                .replace("${BLOCK_HANDLE_FIELD}", policy.legacy() && !policy.fabric()
                        ? "private net.minecraftforge.registries.RegistryObject<Block> blockHandle; public net.minecraftforge.registries.RegistryObject<Block> blockHandle() { return blockHandle; }" : "")
                .replace("${BLOCK_REMOVAL}", policy.unobfuscated() ? "" : blockRemoval())
                .replace("${FLUID_BUCKET_HELPER}", FluidBucketSources.helper())
                .replace("${FLUID_BUCKET_HOOK}", FluidBucketSources.itemHook(policy))
                .replace("${LEGACY_FLUID_BUCKET}", policy.legacy()
                        ? "if (handleFluidBucket(level, pos, player, hand, hit)) return net.minecraft.world.InteractionResult.SUCCESS;" : "")
                .replace("${LEGACY_OVERRIDE_ANNOTATION}", policy.legacy() ? "@SuppressWarnings(\"deprecation\") " : "")
                .replace("${REMOVE_VISIBILITY}", policy.legacy() ? "public" : "protected")
                .replace("${SAVE_ARGS}", policy.legacy() ? "CompoundTag tag" : "CompoundTag tag, HolderLookup.Provider registries")
                .replace("${SAVE_SUPER_ARGS}", policy.legacy() ? "tag" : "tag, registries")
                .replace("${REGISTRY_CONTEXT}", policy.legacy() ? "null" : "registries")
                .replace("${RENDER_NOTIFY}", "notifyRenderSnapshot();")
                .replace("${RENDER_LOAD}", RenderSnapshotSources.load(policy))
                .replace("${RENDER_SNAPSHOT}", RenderSnapshotSources.emit(policy))
                .replace("${LOAD_SIGNATURE}", policy.legacy() ? "public void load(CompoundTag tag)" : "protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries)")
                .replace("${LOAD_SUPER}", policy.legacy() ? "super.load(tag)" : "super.loadAdditional(tag, registries)")
                .replace("${STACK_ENCODE}", policy.legacy() ? "stack.save(new CompoundTag())" : "ItemStack.CODEC.encodeStart(registries().createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow()")
                .replace("${STACK_DECODE}", policy.legacy() ? "ItemStack.of(tag)" : "ItemStack.CODEC.parse(registries().createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow()")
                .replace("${NBT_ACCOUNTER}", policy.legacy() ? "new NbtAccounter(PortableBlockEntityStorage.MAXIMUM_STACK_BYTES)" : "NbtAccounter.create(PortableBlockEntityStorage.MAXIMUM_STACK_BYTES)")
                .replace("${SOUND_LOOKUP}", policy.legacy()
                        ? "BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.tryParse(id.toString()))"
                        : policy.modernRecipes()
                                ? "BuiltInRegistries.SOUND_EVENT.getValue(ResourceLocation.parse(id.toString()))"
                                : "BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(id.toString()))")
                .replace("${CONTAINER_USER_TYPE}", policy.unobfuscated()
                        ? "net.minecraft.world.entity.ContainerUser" : "Player")
                .replace("${CONTAINER_PLAYER_CAST}", policy.unobfuscated()
                        ? "if (!(user instanceof Player player)) return;" : "Player player = user;")
                .replace("${BLOCK_USE_SIGNATURE}", policy.legacy()
                        ? "public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit)"
                        : "protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.phys.BlockHitResult hit)")
                .replace("${BUILDER_IMPORT}", policy.builderImport()).replace("${TYPE_FACTORY}", policy.typeFactory())
                .replace("${TYPE_BUILD_ARGUMENT}", policy.typeBuildArgument());
        return policy.unobfuscated() ? BlockEntity26Sources.adapt(source) : source;
    }

    private static String blockRemoval() {
        return """
                        @Override
                        ${LEGACY_OVERRIDE_ANNOTATION}${REMOVE_VISIBILITY} void onRemove(BlockState state, Level level, BlockPos pos,
                                                BlockState next, boolean moved) {
                            if (!state.is(next.getBlock()) && !level.isClientSide
                                    && level.getBlockEntity(pos) instanceof StoredBlockEntity entity) {
                                Containers.dropContents(level, pos, entity.inventory);
                                entity.inventory.clearContent();
                                level.updateNeighbourForOutputSignal(pos, this);
                            }
                            super.onRemove(state, level, pos, next, moved);
                        }
                """;
    }
}
