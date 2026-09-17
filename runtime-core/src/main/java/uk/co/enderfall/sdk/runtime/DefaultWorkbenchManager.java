package uk.co.enderfall.sdk.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.registry.ItemRef;
import uk.co.enderfall.sdk.api.ui.WorkbenchCraftContext;
import uk.co.enderfall.sdk.api.ui.WorkbenchCraftHandler;
import uk.co.enderfall.sdk.api.ui.WorkbenchManager;
import uk.co.enderfall.sdk.api.ui.WorkbenchRef;
import uk.co.enderfall.sdk.api.ui.WorkbenchSpec;

/** Registration and opening service for target-native, inventory-backed workbench menus. */
public final class DefaultWorkbenchManager implements WorkbenchManager {
    private final String modId;
    private final String target;
    private final PlatformAdapter adapter;
    private final RegistrationGateAccess gate;
    private final Map<ResourceId, PortableWorkbenchDefinition> registrations = new LinkedHashMap<>();

    public DefaultWorkbenchManager(String modId, String target, PlatformAdapter adapter,
                                   RegistrationGateAccess gate) {
        this.modId = Objects.requireNonNull(modId, "modId");
        this.target = Objects.requireNonNull(target, "target");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.gate = Objects.requireNonNull(gate, "gate");
    }

    @Override
    public synchronized WorkbenchRef register(ResourceId id, WorkbenchSpec spec, WorkbenchCraftHandler handler) {
        gate.requireOpen(modId, target);
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(handler, "handler");
        if (!id.namespace().equals(modId)) {
            throw new IllegalArgumentException("[" + modId + "] Workbench ID must use the consumer namespace: " + id);
        }
        spec.storage().ifPresent(storage -> {
            if (!storage.block().id().namespace().equals(modId)) {
                throw new IllegalArgumentException("[" + modId + "] Persistent workbench must use an owned block on " + target);
            }
            if (!adapter.supportsPersistentWorkbenches()) {
                throw new UnsupportedOperationException("[" + modId + "] Persistent workbenches are not enabled on " + target);
            }
        });
        WorkbenchRef reference = new WorkbenchRef(id);
        if (spec.processingTicks() > 0 && !adapter.supportsTimedWorkbenches()) {
            throw new UnsupportedOperationException("[" + modId + "] Timed workbenches are not enabled on " + target);
        }
        PortableWorkbenchDefinition definition = new PortableWorkbenchDefinition(reference, spec, craft ->
                handler.crafted(new WorkbenchCraftContext(craft.playerId(), reference, craft.recipeId(),
                        new ItemRef(craft.result()), craft.resultCount())));
        if (registrations.putIfAbsent(id, definition) != null) {
            throw new IllegalStateException("[" + modId + "] Duplicate workbench " + id + " on " + target);
        }
        if (spec.storage().isPresent()) adapter.registerPersistentWorkbench(definition);
        else adapter.registerWorkbench(definition);
        return reference;
    }

    @Override
    public WorkbenchRef register(String path, WorkbenchSpec spec, WorkbenchCraftHandler handler) {
        return register(ResourceId.of(modId, path), spec, handler);
    }

    @Override
    public void open(UUID playerId, WorkbenchRef workbench) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(workbench, "workbench");
        PortableWorkbenchDefinition definition = registrations.get(workbench.id());
        if (definition == null) {
            throw new IllegalArgumentException("[" + modId + "] Unknown workbench " + workbench.id()
                    + " on " + target);
        }
        if (definition.spec().storage().isPresent()) {
            throw new IllegalArgumentException("[" + modId + "] Persistent workbench requires openAt with a block location on " + target);
        }
        adapter.openWorkbench(playerId, definition);
    }

    @Override
    public void openAt(UUID playerId, WorkbenchRef workbench,
            uk.co.enderfall.sdk.api.blockentity.BlockLocation location) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(workbench, "workbench");
        Objects.requireNonNull(location, "location");
        PortableWorkbenchDefinition definition = registrations.get(workbench.id());
        if (definition == null || definition.spec().storage().isEmpty()) {
            throw new IllegalArgumentException("[" + modId + "] Unknown persistent workbench " + workbench.id() + " on " + target);
        }
        adapter.openPersistentWorkbench(playerId, definition, location);
    }
}
