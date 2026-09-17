package uk.co.enderfall.sdk.runtime.fabric.v1_21_4;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import uk.co.enderfall.sdk.runtime.RuntimeModBootstrap;
import uk.co.enderfall.sdk.runtime.RuntimeModContext;

/** Called by generated consumer entrypoints; not part of the stable portable API. */
public final class FabricConsumerBootstrap {
    private static final Map<String, RuntimeModContext> CONTEXTS = new ConcurrentHashMap<>();

    private FabricConsumerBootstrap() {
    }

    /**
     * Initializes one generated Fabric consumer entrypoint.
     *
     * @param modId consumer mod identifier
     * @param commonEntrypoint portable common entrypoint class
     * @param clientEntrypoint optional portable client entrypoint class
     */
    public static void initialize(String modId, String commonEntrypoint, String clientEntrypoint) {
        FabricPlatformAdapter adapter = new FabricPlatformAdapter(modId);
        RuntimeModContext context = RuntimeModBootstrap.initialize(
                modId, commonEntrypoint, clientEntrypoint, adapter,
                Thread.currentThread().getContextClassLoader());
        if (CONTEXTS.putIfAbsent(modId, context) != null) {
            throw new IllegalStateException("[" + modId + "] EnderFall consumer initialized twice");
        }
        adapter.attach(context);
    }

    static RuntimeModContext context(String modId) {
        RuntimeModContext context = CONTEXTS.get(modId);
        if (context == null) {
            throw new IllegalStateException("[" + modId + "] EnderFall consumer has not initialized");
        }
        return context;
    }
}
