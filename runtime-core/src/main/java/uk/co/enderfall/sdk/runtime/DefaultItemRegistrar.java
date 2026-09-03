package uk.co.enderfall.sdk.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.registry.ItemRef;
import uk.co.enderfall.sdk.api.registry.ItemRegistrar;
import uk.co.enderfall.sdk.api.registry.ItemSpec;

final class DefaultItemRegistrar implements ItemRegistrar {
    private final String modId;
    private final String target;
    private final PlatformAdapter adapter;
    private final RegistrationGate gate;
    private final Map<ResourceId, ItemRef> items = new LinkedHashMap<>();

    DefaultItemRegistrar(String modId, String target, PlatformAdapter adapter, RegistrationGate gate) {
        this.modId = modId;
        this.target = target;
        this.adapter = adapter;
        this.gate = gate;
    }

    @Override
    public ItemRef register(ResourceId id, ItemSpec spec) {
        gate.requireOpen(modId, target);
        requireOwned(id);
        Objects.requireNonNull(spec, "spec");
        ItemRef reference = reserve(id);
        adapter.registerItem(id, spec);
        return reference;
    }

    @Override
    public ItemRef register(String path, ItemSpec spec) {
        return register(ResourceId.of(modId, path), spec);
    }

    ItemRef reserve(ResourceId id) {
        ItemRef reference = new ItemRef(id);
        if (items.putIfAbsent(id, reference) != null) {
            throw new IllegalStateException("[" + modId + "] Duplicate item ID " + id + " on " + target);
        }
        return reference;
    }

    private void requireOwned(ResourceId id) {
        if (!modId.equals(id.namespace())) {
            throw new IllegalArgumentException("[" + modId + "] Cannot register item in namespace " + id.namespace());
        }
    }
}
