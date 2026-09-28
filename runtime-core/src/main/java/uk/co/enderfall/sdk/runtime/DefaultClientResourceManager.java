package uk.co.enderfall.sdk.runtime;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.client.resource.ClientResourceManager;
import uk.co.enderfall.sdk.api.client.resource.ClientResourceReloadListener;
import uk.co.enderfall.sdk.api.event.Subscription;
import uk.co.enderfall.sdk.api.logging.ModLogger;
import uk.co.enderfall.sdk.api.platform.Environment;

/** Shared ownership, lifecycle, and listener-failure handling for client resources. */
public final class DefaultClientResourceManager implements ClientResourceManager {
    private final String modId;
    private final String target;
    private final PlatformAdapter adapter;
    private final RegistrationGateAccess gate;
    private final ModLogger logger;
    private final Set<ResourceId> registered = new HashSet<>();

    public DefaultClientResourceManager(String modId, String target, PlatformAdapter adapter,
            RegistrationGateAccess gate, ModLogger logger) {
        this.modId = Objects.requireNonNull(modId, "modId");
        this.target = Objects.requireNonNull(target, "target");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public synchronized Subscription onReload(ResourceId id, ClientResourceReloadListener listener) {
        gate.requireOpen(modId, target);
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(listener, "listener");
        requireClient();
        if (!id.namespace().equals(modId)) {
            throw new IllegalArgumentException(prefix() + "Reload-listener ID must belong to the consumer: " + id);
        }
        if (!registered.add(id)) {
            throw new IllegalStateException(prefix() + "Duplicate client resource reload listener: " + id);
        }
        AtomicBoolean active = new AtomicBoolean(true);
        try {
            adapter.registerClientResourceReloadListener(id, () -> {
                if (!active.get()) return;
                try {
                    listener.resourcesReloaded();
                } catch (Throwable failure) {
                    logger.error("Client resource reload listener " + id + " failed", failure);
                }
            });
        } catch (UnsupportedOperationException unsupported) {
            registered.remove(id);
            throw new UnsupportedOperationException(prefix() + "Portable client resources are unavailable", unsupported);
        }
        return new Subscription() {
            @Override public boolean active() { return active.get(); }
            @Override public void close() { active.set(false); }
        };
    }

    @Override
    public CompletionStage<Void> reload() {
        requireClient();
        return adapter.reloadClientResources();
    }

    private void requireClient() {
        if (adapter.platformInfo().environment() != Environment.CLIENT) {
            throw new IllegalStateException(prefix() + "Client resources require client initialization");
        }
    }

    private String prefix() {
        return "[" + modId + " on " + target + "] ";
    }
}
