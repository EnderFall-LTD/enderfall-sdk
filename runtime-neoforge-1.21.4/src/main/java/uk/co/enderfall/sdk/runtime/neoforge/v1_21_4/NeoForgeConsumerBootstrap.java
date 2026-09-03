package uk.co.enderfall.sdk.runtime.neoforge.v1_21_4;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.neoforged.bus.api.IEventBus;
import uk.co.enderfall.sdk.runtime.RuntimeModBootstrap;
import uk.co.enderfall.sdk.runtime.RuntimeModContext;

/** Called by generated consumer entrypoints; not part of the stable portable API. */
public final class NeoForgeConsumerBootstrap {
    private static final Map<String, RuntimeModContext> CONTEXTS = new ConcurrentHashMap<>();

    private NeoForgeConsumerBootstrap() {
    }

    /**
     * Initializes one generated NeoForge consumer entrypoint.
     *
     * @param modId consumer mod identifier
     * @param commonEntrypoint portable common entrypoint class
     * @param clientEntrypoint optional portable client entrypoint class
     * @param modBus consumer mod event bus
     * @param consumerClassLoader class loader used to create portable entrypoints
     */
    public static void initialize(String modId, String commonEntrypoint, String clientEntrypoint,
                                  IEventBus modBus, ClassLoader consumerClassLoader) {
        NeoForgePlatformAdapter adapter = new NeoForgePlatformAdapter(modId, modBus);
        RuntimeModContext context = RuntimeModBootstrap.initialize(
                modId, commonEntrypoint, clientEntrypoint, adapter,
                consumerClassLoader);
        if (CONTEXTS.putIfAbsent(modId, context) != null) {
            throw new IllegalStateException("[" + modId + "] EnderFall consumer initialized twice");
        }
        adapter.attach(context);
    }
}
