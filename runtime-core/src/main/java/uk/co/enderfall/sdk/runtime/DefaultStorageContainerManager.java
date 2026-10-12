package uk.co.enderfall.sdk.runtime;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.blockentity.BlockLocation;
import uk.co.enderfall.sdk.api.ui.StorageContainerManager;
import uk.co.enderfall.sdk.api.ui.StorageContainerRef;
import uk.co.enderfall.sdk.api.ui.StorageContainerSpec;

/** Registration and opening service for ordinary block-owned inventories. */
public final class DefaultStorageContainerManager implements StorageContainerManager {
    private final String modId;
    private final String target;
    private final PlatformAdapter adapter;
    private final RegistrationGateAccess gate;
    // Only read and put, never iterated, so a concurrent map matches the runtime's other
    // registration tables. Readers on the server thread no longer observe a plain HashMap that a
    // registration is mutating under a different lock. Writes happen only in synchronized register.
    private final Map<ResourceId, PortableStorageContainerDefinition> registrations =
            new java.util.concurrent.ConcurrentHashMap<>();

    public DefaultStorageContainerManager(String modId, String target, PlatformAdapter adapter,
            RegistrationGateAccess gate) {
        this.modId = Objects.requireNonNull(modId, "modId");
        this.target = Objects.requireNonNull(target, "target");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.gate = Objects.requireNonNull(gate, "gate");
    }

    @Override
    public synchronized StorageContainerRef register(ResourceId id, StorageContainerSpec spec) {
        gate.requireOpen(modId, target);
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(spec, "spec");
        if (!id.namespace().equals(modId)) {
            throw new IllegalArgumentException("[" + modId + "] Container ID must use the consumer namespace: " + id);
        }
        if (!spec.storage().block().id().namespace().equals(modId)) {
            throw new IllegalArgumentException("[" + modId + "] Container must use an owned storage block on " + target);
        }
        if (!adapter.supportsStorageContainers()) {
            throw new UnsupportedOperationException("[" + modId + "] Storage containers are not enabled on " + target);
        }
        StorageContainerRef reference = new StorageContainerRef(id);
        PortableStorageContainerDefinition definition = new PortableStorageContainerDefinition(reference, spec);
        if (registrations.containsKey(id)) {
            throw new IllegalStateException("[" + modId + "] Duplicate storage container " + id + " on " + target);
        }
        adapter.registerStorageContainer(definition);
        // Published only once native registration succeeds: a rejected container keeps its ID free
        // for a corrected retry, and openAt() never reaches a menu the loader does not have yet.
        registrations.put(id, definition);
        return reference;
    }

    @Override
    public StorageContainerRef register(String path, StorageContainerSpec spec) {
        return register(ResourceId.of(modId, path), spec);
    }

    @Override
    public void openAt(UUID playerId, StorageContainerRef container, BlockLocation location) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(location, "location");
        PortableStorageContainerDefinition definition = registrations.get(container.id());
        if (definition == null) {
            throw new IllegalArgumentException("[" + modId + "] Unknown storage container "
                    + container.id() + " on " + target);
        }
        adapter.openStorageContainer(playerId, definition, location);
    }
}
