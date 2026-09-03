package uk.co.enderfall.sdk.runtime;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.registry.CreativeTabRef;
import uk.co.enderfall.sdk.api.registry.CreativeTabRegistrar;
import uk.co.enderfall.sdk.api.registry.CreativeTabSpec;

final class DefaultCreativeTabRegistrar implements CreativeTabRegistrar {
    private final String modId;
    private final String target;
    private final PlatformAdapter adapter;
    private final RegistrationGate gate;
    private final Set<ResourceId> registered = new HashSet<>();

    DefaultCreativeTabRegistrar(String modId, String target, PlatformAdapter adapter, RegistrationGate gate) {
        this.modId = modId;
        this.target = target;
        this.adapter = adapter;
        this.gate = gate;
    }

    @Override
    public CreativeTabRef register(ResourceId id, CreativeTabSpec spec) {
        gate.requireOpen(modId, target);
        if (!modId.equals(id.namespace())) {
            throw new IllegalArgumentException("[" + modId + "] Cannot register creative tab in namespace "
                    + id.namespace());
        }
        Objects.requireNonNull(spec, "spec");
        if (!registered.add(id)) {
            throw new IllegalStateException("[" + modId + "] Duplicate creative tab ID " + id + " on " + target);
        }
        adapter.registerCreativeTab(id, spec);
        return new CreativeTabRef(id);
    }

    @Override
    public CreativeTabRef register(String path, CreativeTabSpec spec) {
        return register(ResourceId.of(modId, path), spec);
    }
}
