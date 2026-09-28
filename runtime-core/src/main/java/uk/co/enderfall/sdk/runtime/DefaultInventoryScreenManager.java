package uk.co.enderfall.sdk.runtime;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import uk.co.enderfall.sdk.api.client.ui.InventoryScreenManager;
import uk.co.enderfall.sdk.api.client.ui.PortableInventoryScreen;
import uk.co.enderfall.sdk.api.platform.Environment;
import uk.co.enderfall.sdk.api.ui.StorageContainerRef;

/** Shared ownership and lifecycle validation for authored inventory presentations. */
public final class DefaultInventoryScreenManager implements InventoryScreenManager {
    private final String modId;
    private final String target;
    private final PlatformAdapter adapter;
    private final RegistrationGateAccess gate;
    private final Set<StorageContainerRef> registered = new HashSet<>();

    public DefaultInventoryScreenManager(String modId, String target,
            PlatformAdapter adapter, RegistrationGateAccess gate) {
        this.modId = Objects.requireNonNull(modId, "modId");
        this.target = Objects.requireNonNull(target, "target");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.gate = Objects.requireNonNull(gate, "gate");
    }

    @Override
    public synchronized void register(StorageContainerRef container,
            Supplier<? extends PortableInventoryScreen> factory) {
        gate.requireOpen(modId, target);
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(factory, "factory");
        requireClient();
        if (!container.id().namespace().equals(modId)) {
            throw new IllegalArgumentException(prefix() + "Inventory screen container must belong to the consumer: "
                    + container.id());
        }
        if (!registered.add(container)) {
            throw new IllegalStateException(prefix() + "Duplicate portable inventory screen: " + container.id());
        }
        try {
            adapter.registerInventoryScreen(container, factory);
        } catch (RuntimeException failure) {
            registered.remove(container);
            throw failure;
        }
    }

    private void requireClient() {
        if (adapter.platformInfo().environment() != Environment.CLIENT) {
            throw new IllegalStateException(prefix() + "Portable inventory screens require client initialization");
        }
    }

    private String prefix() { return "[" + modId + " on " + target + "] "; }
}
