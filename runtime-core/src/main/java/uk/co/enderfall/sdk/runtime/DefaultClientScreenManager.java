package uk.co.enderfall.sdk.runtime;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.client.ui.ClientScreenManager;
import uk.co.enderfall.sdk.api.client.ui.ClientScreenRef;
import uk.co.enderfall.sdk.api.client.ui.ClientScreenSpec;
import uk.co.enderfall.sdk.api.client.ui.PortableClientScreen;
import uk.co.enderfall.sdk.api.platform.Environment;

/** Shared ownership and lifecycle validation for portable client screens. */
public final class DefaultClientScreenManager implements ClientScreenManager {
    private final String modId;
    private final String target;
    private final PlatformAdapter adapter;
    private final RegistrationGateAccess gate;
    private final Set<ResourceId> registered = new HashSet<>();

    public DefaultClientScreenManager(String modId, String target,
            PlatformAdapter adapter, RegistrationGateAccess gate) {
        this.modId = Objects.requireNonNull(modId, "modId");
        this.target = Objects.requireNonNull(target, "target");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.gate = Objects.requireNonNull(gate, "gate");
    }

    @Override
    public synchronized ClientScreenRef register(ResourceId id, ClientScreenSpec spec,
            Supplier<? extends PortableClientScreen> factory) {
        gate.requireOpen(modId, target);
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(factory, "factory");
        requireClient();
        String prefix = prefix();
        if (!id.namespace().equals(modId)) {
            throw new IllegalArgumentException(prefix + "Screen ID must belong to the consumer: " + id);
        }
        if (registered.contains(id)) {
            throw new IllegalStateException(prefix + "Duplicate client screen: " + id);
        }
        try {
            adapter.registerClientScreen(id, spec, factory);
        } catch (UnsupportedOperationException unsupported) {
            throw new UnsupportedOperationException(prefix + "Portable client screens are unavailable", unsupported);
        }
        registered.add(id);
        return new ClientScreenRef(id);
    }

    @Override
    public synchronized void open(ClientScreenRef screen) {
        Objects.requireNonNull(screen, "screen");
        requireClient();
        if (!registered.contains(screen.id())) {
            throw new IllegalArgumentException(prefix() + "Client screen is not registered by this mod: "
                    + screen.id());
        }
        adapter.openClientScreen(screen.id());
    }

    @Override
    public void close() {
        requireClient();
        adapter.closeClientScreen();
    }

    private void requireClient() {
        if (adapter.platformInfo().environment() != Environment.CLIENT) {
            throw new IllegalStateException(prefix() + "Portable screens require client initialization");
        }
    }

    private String prefix() {
        return "[" + modId + " on " + target + "] ";
    }
}
