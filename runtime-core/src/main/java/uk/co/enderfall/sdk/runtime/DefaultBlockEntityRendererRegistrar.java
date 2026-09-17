package uk.co.enderfall.sdk.runtime;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.platform.Environment;
import uk.co.enderfall.sdk.api.registry.BlockRef;
import uk.co.enderfall.sdk.api.render.BlockEntityRendererRegistrar;
import uk.co.enderfall.sdk.api.render.BlockEntityRenderSpec;

/** Shared validation; no client or Minecraft classes are loaded here. */
public final class DefaultBlockEntityRendererRegistrar implements BlockEntityRendererRegistrar {
    private final String modId;
    private final String target;
    private final PlatformAdapter adapter;
    private final RegistrationGateAccess gate;
    private final Set<ResourceId> registered = new HashSet<>();

    public DefaultBlockEntityRendererRegistrar(String modId, String target,
            PlatformAdapter adapter, RegistrationGateAccess gate) {
        this.modId = Objects.requireNonNull(modId, "modId");
        this.target = Objects.requireNonNull(target, "target");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.gate = Objects.requireNonNull(gate, "gate");
    }

    @Override public synchronized void register(BlockRef block, BlockEntityRenderSpec spec) {
        gate.requireOpen(modId, target);
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(spec, "spec");
        String prefix = "[" + modId + " on " + target + "] ";
        if (adapter.platformInfo().environment() != Environment.CLIENT) {
            throw new IllegalStateException(prefix + "Renderers require client initialization");
        }
        if (!block.id().namespace().equals(modId)) {
            throw new IllegalArgumentException(prefix + "Renderer block must belong to the consumer");
        }
        if (registered.contains(block.id())) {
            throw new IllegalStateException(prefix + "Duplicate renderer for " + block.id());
        }
        try {
            adapter.registerBlockEntityRenderer(block, spec);
        } catch (UnsupportedOperationException unsupported) {
            throw new UnsupportedOperationException(prefix + "Block-entity rendering is unavailable", unsupported);
        }
        registered.add(block.id());
    }
}
