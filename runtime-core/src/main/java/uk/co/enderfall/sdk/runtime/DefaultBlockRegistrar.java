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
        BlockRef reference = new BlockRef(id);
        if (blocks.putIfAbsent(id, reference) != null) {
            throw new IllegalStateException("[" + modId + "] Duplicate block ID " + id + " on " + target);
        }
        adapter.registerBlock(id, spec, blockItemSpec);
        return reference;
    }
}
