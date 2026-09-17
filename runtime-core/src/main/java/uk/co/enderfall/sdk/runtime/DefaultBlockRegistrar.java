package uk.co.enderfall.sdk.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.registry.BlockRef;
import uk.co.enderfall.sdk.api.registry.BlockRegistrar;
import uk.co.enderfall.sdk.api.registry.BlockSpec;
import uk.co.enderfall.sdk.api.registry.ItemSpec;

final class DefaultBlockRegistrar implements BlockRegistrar {
    private final String modId;
    private final String target;
    private final PlatformAdapter adapter;
    private final RegistrationGate gate;
    private final DefaultItemRegistrar items;
    private final Map<ResourceId, BlockRef> blocks = new LinkedHashMap<>();

    DefaultBlockRegistrar(String modId, String target, PlatformAdapter adapter, RegistrationGate gate,
                          DefaultItemRegistrar items) {
        this.modId = modId;
        this.target = target;
        this.adapter = adapter;
        this.gate = gate;
        this.items = items;
    }

    private void requirePropertyCopy(BlockSpec spec) {
        if (spec.stateShapes().isPresent() && !adapter.supportsStateShapes()) {
            throw new UnsupportedOperationException("[" + modId + "] State-dependent shapes are unavailable on " + target);
        }
        if (!spec.states().properties().isEmpty() && !adapter.supportsBlockStates()) {
            throw new UnsupportedOperationException("[" + modId + "] Custom block states are unavailable on " + target);
        }
        if (spec.sixWayFacing() && !adapter.supportsSixWayFacing()) {
            throw new UnsupportedOperationException("[" + modId + "] Six-way facing is unavailable on " + target);
        }
        if (spec.horizontalFacing() && !adapter.supportsHorizontalFacing()) {
            throw new UnsupportedOperationException("[" + modId + "] Horizontal facing is unavailable on " + target);
        }
        if (spec.scheduledTicks() && !adapter.supportsScheduledBlockTicks()) {
            throw new UnsupportedOperationException("[" + modId + "] Scheduled block ticks are unavailable on " + target);
        }
        if (spec.waterlogged() && !adapter.supportsWaterloggedBlocks()) {
            throw new UnsupportedOperationException("[" + modId + "] Waterlogged blocks are unavailable on " + target);
        }
        if ((spec.outlineShape().isPresent() || spec.collisionShape().isPresent()) && !adapter.supportsBlockShapes()) {
            throw new UnsupportedOperationException("[" + modId + "] Custom block shapes are unavailable on " + target);
        }
        if (spec.copySource().isPresent() && !adapter.supportsBlockPropertyCopy()) {
            throw new UnsupportedOperationException("[" + modId + "] Native block property copying requires a generated feature runtime on " + target);
        }
    }

    @Override
    public BlockRef register(ResourceId id, BlockSpec spec) {
        return registerInternal(id, spec, null);
    }

    @Override
    public BlockRef register(String path, BlockSpec spec) {
        return register(ResourceId.of(modId, path), spec);
    }

    @Override
    public BlockRef registerWithItem(String path, BlockSpec blockSpec, ItemSpec itemSpec) {
        gate.requireOpen(modId, target);
        requirePropertyCopy(Objects.requireNonNull(blockSpec, "blockSpec"));
        Objects.requireNonNull(itemSpec, "itemSpec");
        ResourceId id = ResourceId.of(modId, path);
        items.reserve(id);
        return registerInternal(id, blockSpec, Objects.requireNonNull(itemSpec, "itemSpec"));
    }

    private BlockRef registerInternal(ResourceId id, BlockSpec spec, ItemSpec blockItemSpec) {
        gate.requireOpen(modId, target);
        if (!modId.equals(id.namespace())) {
            throw new IllegalArgumentException("[" + modId + "] Cannot register block in namespace " + id.namespace());
        }
        Objects.requireNonNull(spec, "spec");
        requirePropertyCopy(spec);
        BlockRef reference = new BlockRef(id);
        if (blocks.putIfAbsent(id, reference) != null) {
            throw new IllegalStateException("[" + modId + "] Duplicate block ID " + id + " on " + target);
        }
        adapter.registerBlock(id, spec, blockItemSpec);
        return reference;
    }

    @Override
    public BlockRef registerPersistentWithItem(String path, BlockSpec blockSpec, ItemSpec itemSpec,
            uk.co.enderfall.sdk.api.blockentity.BlockEntitySpec storage) {
        gate.requireOpen(modId, target);
        ResourceId id = ResourceId.of(modId, path);
        Objects.requireNonNull(blockSpec, "blockSpec");
        requirePropertyCopy(blockSpec);
        Objects.requireNonNull(itemSpec, "itemSpec");
        Objects.requireNonNull(storage, "storage");
        if (!storage.block().id().equals(id)) {
            throw new IllegalArgumentException("[" + modId + "] Persistent storage must belong to " + id + " on " + target);
        }
        if (!adapter.supportsPersistentWorkbenches()) {
            throw new UnsupportedOperationException("[" + modId + "] Persistent blocks are not enabled on " + target);
        }
        if (blocks.containsKey(id)) {
            throw new IllegalStateException("[" + modId + "] Duplicate block ID " + id + " on " + target);
        }
        // Reserve before native registration: a partially failed native registration must stop startup.
        items.reserve(id);
        BlockRef reference = new BlockRef(id);
        blocks.put(id, reference);
        adapter.registerPersistentBlock(id, blockSpec, itemSpec, storage);
        return reference;
    }
}
