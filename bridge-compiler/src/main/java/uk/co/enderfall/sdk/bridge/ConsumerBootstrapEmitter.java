package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Shared consumer initialization with explicit class-loader and mod-event-bus policies. */
final class ConsumerBootstrapEmitter {
    private ConsumerBootstrapEmitter() { }

    private record Plan(LoaderAbi loader) {
        boolean fabric() { return loader == LoaderAbi.FABRIC; }
        boolean legacy() { return loader == LoaderAbi.LEGACY_FML; }
        String prefix() { return fabric() ? "Fabric" : legacy() ? "LegacyForge" : "NeoForge"; }
        String root() { return "uk/co/enderfall/sdk/runtime/" + (fabric() ? "fabric/v1_21_4/" : legacy() ? "forge/v1_20_1/" : "neoforge/v1_21_4/"); }
    }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> paths) throws BridgeGenerationException {
        return emitIfPresent(target, paths, false);
    }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> paths, boolean persistence) throws BridgeGenerationException {
        if (!TargetCatalog.standard().require(target.id()).equals(target)) throw new BridgeGenerationException("Unreviewed consumer-bootstrap target " + target.id());
        Plan p = new Plan(target.loaderAbi());
        String canonicalRoot = "uk/co/enderfall/sdk/runtime/" + (p.fabric() ? "fabric/v1_21_4/Fabric" : "neoforge/v1_21_4/NeoForge");
        String canonical = canonicalRoot + "ConsumerBootstrap.java";
        if (!paths.contains(canonical)) return List.of();
        if (!paths.contains(canonicalRoot + "PlatformAdapter.java")) throw new BridgeGenerationException(target.id() + " requires a platform adapter for consumer initialization");
        String source = render(p, persistence);
        if (persistence) source = source.replace(p.prefix() + "PlatformAdapter", p.prefix() + "PersistentPlatformAdapter");
        return List.of(new RuntimeSource(canonical, p.root() + p.prefix() + "ConsumerBootstrap.java", source.getBytes(StandardCharsets.UTF_8)));
    }

    private static String render(Plan p, boolean persistence) {
        StringBuilder out = new StringBuilder("package " + p.root().substring(0, p.root().length() - 1).replace('/', '.') + ";\n\n");
        out.append("import java.util.Map;\nimport java.util.concurrent.ConcurrentHashMap;\n");
        if (!p.fabric()) out.append(p.legacy() ? "import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;\n" : "import net.neoforged.bus.api.IEventBus;\n");
        out.append("import uk.co.enderfall.sdk.runtime.RuntimeModBootstrap;\nimport uk.co.enderfall.sdk.runtime.RuntimeModContext;\n\n");
        out.append(p.legacy() ? "/** Called by generated 1.20.1 Forge-family consumer entrypoints. */\n"
                : "/** Called by generated consumer entrypoints; not part of the stable portable API. */\n");
        out.append("""
                public final class %1$sConsumerBootstrap {
                    private static final Map<String, RuntimeModContext> CONTEXTS = new ConcurrentHashMap<>();

                    private %1$sConsumerBootstrap() {
                    }

                    /**
                     * Initializes one generated %2$s consumer entrypoint.
                     *
                     * @param modId consumer mod identifier
                     * @param commonEntrypoint portable common entrypoint class
                     * @param clientEntrypoint optional portable client entrypoint class
                """.formatted(p.prefix(), p.fabric() ? "Fabric" : p.legacy() ? "1.20.1 Forge-family" : "NeoForge"));
        if (!p.fabric()) {
            if (!p.legacy()) out.append("     * @param modBus consumer mod event bus\n");
            out.append("     * @param consumerClassLoader class loader used to create portable entrypoints\n");
        }
        out.append("     */\n");
        if (p.legacy()) out.append("    @SuppressWarnings({\"deprecation\", \"removal\"})\n");
        out.append("    public static void initialize(String modId, String commonEntrypoint, String clientEntrypoint");
        out.append(p.fabric() ? ") {\n" : ",\n                                  " + (p.legacy() ? "" : "IEventBus modBus, ") + "ClassLoader consumerClassLoader) {\n");
        if (p.legacy()) out.append("        var modBus = FMLJavaModLoadingContext.get().getModEventBus();\n");
        out.append("        " + p.prefix() + "PlatformAdapter adapter = new " + p.prefix() + "PlatformAdapter(modId" + (p.fabric() ? "" : ", modBus") + ");\n");
        if (persistence) out.append("        adapter.enableMenuGauges();\n");
        out.append("        RuntimeModContext context = RuntimeModBootstrap.initialize(\n                modId, commonEntrypoint, clientEntrypoint, adapter,");
        out.append(p.legacy() ? " consumerClassLoader);\n" : "\n                " + (p.fabric() ? "Thread.currentThread().getContextClassLoader()" : "consumerClassLoader") + ");\n");
        out.append("""
                        if (CONTEXTS.putIfAbsent(modId, context) != null) {
                            throw new IllegalStateException("[" + modId + "] EnderFall consumer initialized twice");
                        }
                        adapter.attach(context);
                    }
                """);
        if (p.fabric()) out.append("""

                    static RuntimeModContext context(String modId) {
                        RuntimeModContext context = CONTEXTS.get(modId);
                        if (context == null) {
                            throw new IllegalStateException("[" + modId + "] EnderFall consumer has not initialized");
                        }
                        return context;
                    }
                """);
        return out.append("}\n").toString();
    }
}
