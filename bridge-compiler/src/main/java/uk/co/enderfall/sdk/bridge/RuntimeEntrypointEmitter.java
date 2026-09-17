package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Emits loader-owned SDK markers; consumer bootstraps still own mod initialization. */
final class RuntimeEntrypointEmitter {
    private RuntimeEntrypointEmitter() { }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> paths) throws BridgeGenerationException {
        if (!TargetCatalog.standard().require(target.id()).equals(target)) throw new BridgeGenerationException("Unreviewed runtime-entrypoint target " + target.id());
        boolean fabric = target.loaderAbi() == LoaderAbi.FABRIC;
        boolean legacy = target.loaderAbi() == LoaderAbi.LEGACY_FML;
        String canonical = "uk/co/enderfall/sdk/runtime/" + (fabric ? "fabric/v1_21_4/EnderfallFabricRuntime.java" : "neoforge/v1_21_4/EnderfallNeoForgeRuntime.java");
        if (!paths.contains(canonical)) return List.of();
        String root = legacy ? "uk/co/enderfall/sdk/runtime/forge/v1_20_1/" : canonical.substring(0, canonical.lastIndexOf('/') + 1);
        String name = fabric ? "EnderfallFabricRuntime" : legacy ? "EnderfallForgeRuntime" : "EnderfallNeoForgeRuntime";
        StringBuilder out = new StringBuilder("package " + root.substring(0, root.length() - 1).replace('/', '.') + ";\n\n");
        out.append("import ").append(fabric ? "net.fabricmc.api.ModInitializer" : legacy ? "net.minecraftforge.fml.common.Mod" : "net.neoforged.fml.common.Mod").append(";\n\n");
        // Preserve the inherited reference description and binary names across target versions.
        out.append("/** ").append(fabric ? "Fabric-owned entrypoint for the 1.21.4" : legacy ? "Loader-owned entrypoint for the 1.20.1" : "NeoForge-owned entrypoint for the 1.21.4")
                .append(" EnderFall runtime artifact. */\n");
        if (!fabric) out.append("@Mod(\"enderfall_sdk\")\n");
        out.append("public final class ").append(name).append(fabric ? " implements ModInitializer" : "").append(" {\n")
                .append("    /** Creates the loader-owned runtime marker entrypoint. */\n    public ").append(name).append("() {\n");
        if (fabric) out.append("    }\n\n    @Override\n    public void onInitialize() {\n");
        out.append("        // Consumer bootstrap classes initialize their own isolated runtime contexts.\n    }\n}\n");
        return List.of(new RuntimeSource(canonical, root + name + ".java", out.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
