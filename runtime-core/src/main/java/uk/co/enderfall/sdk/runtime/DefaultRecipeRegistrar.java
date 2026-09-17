package uk.co.enderfall.sdk.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.recipe.RecipeRegistrar;
import uk.co.enderfall.sdk.api.recipe.WorkbenchRecipeTypeRef;

/** Runtime-backed registration gate for custom workbench recipe serializers. */
public final class DefaultRecipeRegistrar implements RecipeRegistrar {
    private final String modId;
    private final String target;
    private final PlatformAdapter adapter;
    private final RegistrationGateAccess gate;
    private final Map<ResourceId, WorkbenchRecipeTypeRef> registrations = new LinkedHashMap<>();

    public DefaultRecipeRegistrar(String modId, String target, PlatformAdapter adapter, RegistrationGateAccess gate) {
        this.modId = Objects.requireNonNull(modId, "modId");
        this.target = Objects.requireNonNull(target, "target");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.gate = Objects.requireNonNull(gate, "gate");
    }

    @Override
    public synchronized WorkbenchRecipeTypeRef registerWorkbenchType(ResourceId id, int inputSlots) {
        gate.requireOpen(modId, target);
        Objects.requireNonNull(id, "id");
        if (!id.namespace().equals(modId)) {
            throw new IllegalArgumentException("[" + modId + "] Recipe type ID must use the consumer namespace: " + id);
        }
        WorkbenchRecipeTypeRef reference = new WorkbenchRecipeTypeRef(id, inputSlots);
        if (registrations.putIfAbsent(id, reference) != null) {
            throw new IllegalStateException("[" + modId + "] Duplicate recipe type " + id + " on " + target);
        }
        adapter.registerWorkbenchRecipeType(reference);
        return reference;
    }

    @Override
    public WorkbenchRecipeTypeRef registerWorkbenchType(String path, int inputSlots) {
        return registerWorkbenchType(ResourceId.of(modId, path), inputSlots);
    }
}
