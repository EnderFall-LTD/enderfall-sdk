package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.MinecraftAbi;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Shared platform queries with explicit native loader and environment-access policies. */
final class PlatformInfoEmitter {
    private PlatformInfoEmitter() { }

    private record Plan(boolean fabric, boolean legacy, boolean modern26) {
        String prefix() { return fabric ? "Fabric" : legacy ? "LegacyForge" : "NeoForge"; }
        String root() { return "uk/co/enderfall/sdk/runtime/" + (fabric ? "fabric/v1_21_4/" : legacy ? "forge/v1_20_1/" : "neoforge/v1_21_4/"); }
        String nativeRoot() { return legacy ? "net.minecraftforge" : "net.neoforged"; }
    }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> paths) throws BridgeGenerationException {
        if (!TargetCatalog.standard().require(target.id()).equals(target)) throw new BridgeGenerationException("Unreviewed platform-info target " + target.id());
        Plan p = new Plan(target.loaderAbi() == LoaderAbi.FABRIC, target.loaderAbi() == LoaderAbi.LEGACY_FML,
                target.minecraftAbi() == MinecraftAbi.V26_2);
        String canonical = "uk/co/enderfall/sdk/runtime/" + (p.fabric() ? "fabric/v1_21_4/Fabric" : "neoforge/v1_21_4/NeoForge") + "PlatformInfo.java";
        if (!paths.contains(canonical)) return List.of();
        String filename = p.prefix() + (!p.fabric() && p.modern26() ? "26" : "") + "PlatformInfo.java";
        return List.of(new RuntimeSource(canonical, p.root() + filename, render(p).getBytes(StandardCharsets.UTF_8)));
    }

    private static String render(Plan p) {
        var imports = new TreeSet<>(List.of("java.util.Optional", "uk.co.enderfall.sdk.api.platform.Environment",
                "uk.co.enderfall.sdk.api.platform.Loader", "uk.co.enderfall.sdk.api.platform.MinecraftVersion",
                "uk.co.enderfall.sdk.api.platform.PlatformInfo"));
        if (p.fabric()) { imports.add("net.fabricmc.api.EnvType"); imports.add("net.fabricmc.loader.api.FabricLoader"); }
        else { imports.add(p.nativeRoot() + ".api.distmarker.Dist"); imports.add(p.nativeRoot() + ".fml.ModList"); imports.add(p.nativeRoot() + ".fml.loading.FMLEnvironment"); }
        if (p.legacy()) imports.addAll(List.of("java.io.IOException", "java.io.InputStream", "java.nio.charset.StandardCharsets"));
        StringBuilder out = new StringBuilder("package " + p.root().substring(0, p.root().length() - 1).replace('/', '.') + ";\n\n");
        imports.forEach(name -> out.append("import ").append(name).append(";\n"));
        out.append("\nfinal class ").append(p.prefix()).append("PlatformInfo implements PlatformInfo {\n");
        if (p.fabric()) out.append("    private final FabricLoader loader = FabricLoader.getInstance();\n\n");
        if (p.legacy()) out.append("    private final Loader loader = readLoader();\n\n");
        method(out, p, "Loader loader()", "return " + (p.fabric() ? "Loader.FABRIC" : p.legacy() ? "loader" : "Loader.NEOFORGE") + ";", true);
        String version = p.legacy() ? "return new MinecraftVersion(\"1.20.1\");" : p.fabric()
                ? "String version = loader.getModContainer(\"minecraft\")\n        .orElseThrow(() -> new IllegalStateException(\"Minecraft mod container is unavailable\"))\n        .getMetadata().getVersion().getFriendlyString();\nreturn new MinecraftVersion(version);"
                : "String version = ModList.get().getModContainerById(\"minecraft\")\n        .map(container -> container.getModInfo().getVersion().toString())\n        .orElseThrow(() -> new IllegalStateException(\"Minecraft mod container is unavailable\"));\nreturn new MinecraftVersion(version);";
        method(out, p, "MinecraftVersion minecraftVersion()", version, true);
        String environment = p.fabric() ? "loader.getEnvironmentType() == EnvType.CLIENT" : p.modern26() ? "FMLEnvironment.getDist() == Dist.CLIENT" : "FMLEnvironment.dist == Dist.CLIENT";
        method(out, p, "Environment environment()", "return " + environment + (p.fabric() || p.modern26() ? "\n        " : " ")
                + "? Environment.CLIENT : Environment.DEDICATED_SERVER;", false);
        method(out, p, "boolean isModLoaded(String modId)", "return " + (p.fabric() ? "loader.isModLoaded(modId)" : "ModList.get().isLoaded(modId)") + ";", true);
        method(out, p, "Optional<String> modVersion(String modId)", p.fabric()
                ? "return loader.getModContainer(modId)\n        .map(container -> container.getMetadata().getVersion().getFriendlyString());"
                : "return ModList.get().getModContainerById(modId)\n        .map(container -> container.getModInfo().getVersion().toString());", false);
        if (p.legacy()) out.append(LOADER_MARKER);
        return out.append("}\n").toString();
    }

    private static void method(StringBuilder out, Plan p, String signature, String body, boolean inlineLegacy) {
        if (p.legacy() && inlineLegacy) out.append("    @Override public ").append(signature).append(" { ").append(body).append(" }\n");
        else {
            out.append(p.legacy() ? "    @Override public " : "    @Override\n    public ").append(signature).append(" {\n");
            for (String line : body.split("\n")) out.append("        ").append(line).append('\n');
            out.append("    }\n");
        }
        if (!p.legacy() && !signature.startsWith("Optional")) out.append('\n');
    }

    private static final String LOADER_MARKER = """

                private static Loader readLoader() {
                    try (InputStream input = LegacyForgePlatformInfo.class.getClassLoader()
                            .getResourceAsStream("META-INF/enderfall-sdk-loader")) {
                        if (input == null) {
                            throw new IllegalStateException("EnderFall loader marker is missing");
                        }
                        return Loader.valueOf(new String(input.readAllBytes(), StandardCharsets.UTF_8)
                                .trim().toUpperCase(java.util.Locale.ROOT));
                    } catch (IOException exception) {
                        throw new IllegalStateException("Cannot read EnderFall loader marker", exception);
                    }
                }
            """;
}
