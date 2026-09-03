package uk.co.enderfall.sdk.runtime.forge.v1_20_1;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import uk.co.enderfall.sdk.runtime.RuntimeModBootstrap;
import uk.co.enderfall.sdk.runtime.RuntimeModContext;

/** Called by generated 1.20.1 Forge-family consumer entrypoints. */
public final class LegacyForgeConsumerBootstrap {
    private static final Map<String, RuntimeModContext> CONTEXTS = new ConcurrentHashMap<>();

    private LegacyForgeConsumerBootstrap() {
    }

    /**
     * Initializes one generated 1.20.1 Forge-family consumer entrypoint.
     *
     * @param modId consumer mod identifier
     * @param commonEntrypoint portable common entrypoint class
     * @param clientEntrypoint optional portable client entrypoint class
     * @param consumerClassLoader class loader used to create portable entrypoints
     */
    @SuppressWarnings({"deprecation", "removal"})
    public static void initialize(String modId, String commonEntrypoint, String clientEntrypoint,
                                  ClassLoader consumerClassLoader) {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        LegacyForgePlatformAdapter adapter = new LegacyForgePlatformAdapter(modId, modBus);
        RuntimeModContext context = RuntimeModBootstrap.initialize(
                modId, commonEntrypoint, clientEntrypoint, adapter, consumerClassLoader);
        if (CONTEXTS.putIfAbsent(modId, context) != null) {
            throw new IllegalStateException("[" + modId + "] EnderFall consumer initialized twice");
        }
        adapter.attach(context);
    }
}
