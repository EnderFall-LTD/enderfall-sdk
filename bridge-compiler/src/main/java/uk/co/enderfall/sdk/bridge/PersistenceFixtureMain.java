package uk.co.enderfall.sdk.bridge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import uk.co.enderfall.sdk.bridge.model.ModRuntimeMetadata;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;

/** Generates test-launcher glue and versioned resources around one unchanged portable fixture. */
public final class PersistenceFixtureMain {
    private static final String ID = "enderfall_persistent_preview";
    private PersistenceFixtureMain() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected target, fixture root, output root");
        var target = TargetCatalog.standard().require(args[0]);
        var policy = BlockEntityNativePolicy.require(args[0]);
        Path fixture = Path.of(args[1]).toAbsolutePath().normalize();
        Path output = Path.of(args[2]).toAbsolutePath().normalize();
        if (output.startsWith(fixture) || fixture.startsWith(output)) throw new IllegalArgumentException("Fixture input/output must be disjoint");
        String bootstrap = policy.fabric() ? "Fabric" : policy.legacy() ? "LegacyForge" : "NeoForge";
        String call = "uk.co.enderfall.sdk.runtime." + policy.runtimePackage() + "." + bootstrap
                + "ConsumerBootstrap.initialize(\"" + ID + "\", PersistentDemo.class.getName(), \"uk.co.enderfall.sdk.preview.PreviewRenderClient\"";
        String body = policy.fabric()
                ? "public final class GeneratedPersistenceEntrypoint implements net.fabricmc.api.ModInitializer { public void onInitialize() { " + call + "); } }"
                : policy.legacy()
                ? "@net.minecraftforge.fml.common.Mod(\"" + ID + "\") public final class GeneratedPersistenceEntrypoint { public GeneratedPersistenceEntrypoint() { " + call + ", getClass().getClassLoader()); } }"
                : "@net.neoforged.fml.common.Mod(\"" + ID + "\") public final class GeneratedPersistenceEntrypoint { public GeneratedPersistenceEntrypoint(net.neoforged.bus.api.IEventBus bus) { " + call + ", bus, getClass().getClassLoader()); } }";
        write(output, "java/uk/co/enderfall/sdk/preview/GeneratedPersistenceEntrypoint.java", "package uk.co.enderfall.sdk.preview;\n" + body + "\n");
        var metadata = new ModRuntimeMetadata(ID, "0.0.0-dev", "EnderFall Persistence Fixture", List.of("EnderFall"),
                "Apache-2.0", "*", "uk.co.enderfall.sdk.preview.GeneratedPersistenceEntrypoint", "Persistence acceptance fixture");
        var resources = switch (target.loaderAbi()) {
            case FABRIC -> FabricRuntimeResourceEmitter.emit(target, metadata);
            case LEGACY_FML -> LegacyFmlResourceEmitter.emit(target, metadata);
            case MODERN_NEOFORGE -> NeoForgeRuntimeResourceEmitter.emit(target, metadata);
        };
        for (var resource : resources) {
            String content = resource.content();
            if (resource.relativePath().equals("fabric.mod.json")) {
                content = content.replace("\"depends\": {", "\"depends\": {\n    \"enderfall_sdk\": \"*\",");
            } else if (resource.relativePath().endsWith(".toml")) {
                content += "\n[[dependencies." + ID + "]]\nmodId=\"enderfall_sdk\"\n"
                        + (policy.legacy() ? "mandatory=true\n" : "type=\"required\"\n")
                        + "versionRange=\"[0.1.0-beta.1,)\"\nordering=\"AFTER\"\nside=\"BOTH\"\n";
            }
            write(output, "resources/" + resource.relativePath(), content);
        }
        Path assets = fixture.resolve("src/main/resources/assets");
        if (Files.isSymbolicLink(assets) || !Files.isDirectory(assets)) throw new IllegalArgumentException("Invalid fixture asset root");
        try (var files = Files.walk(assets)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("Symlink in fixture assets");
                String relative = assets.relativize(file).toString().replace('\\', '/');
                if (!policy.modernRecipes() && relative.contains("/items/")) continue;
                write(output, "resources/assets/" + relative, Files.readString(file));
            }
        }
        Path machines = fixture.resolve("src/main/resources/data/" + ID + "/enderfall_machine");
        if (Files.isDirectory(machines)) {
            try (var files = Files.walk(machines)) {
                for (Path file : files.toList()) {
                    if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("Symlink in machine recipes");
                    if (!Files.isRegularFile(file)) continue;
                    String relative = machines.relativize(file).toString().replace('\\', '/');
                    write(output, "resources/data/" + ID + "/enderfall_machine/" + relative, Files.readString(file));
                }
            }
        }
        for (String block : List.of("workbench", "timed_workbench", "fluid_tank", "orientation_test", "storage_cabinet")) {
            if (!policy.modernRecipes()) write(output, "resources/assets/" + ID + "/models/item/" + block + ".json",
                    "{\"parent\":\"" + ID + ":block/" + block + "\"}\n");
            write(output, "resources/data/" + ID + "/" + (policy.legacy() ? "loot_tables" : "loot_table") + "/blocks/" + block + ".json",
                    Files.readString(fixture.resolve("src/main/resources/data/" + ID + "/loot_table/blocks/" + block + ".json")));
        }
        String ingredients = policy.modernRecipes()
                ? "{\"ingredient\":\"%s\",\"count\":%d}"
                : "{\"ingredient\":{\"item\":\"%s\"},\"count\":%d}";
        write(output, "resources/data/" + ID + "/" + (policy.legacy() ? "recipes" : "recipe") + "/assembly.json",
                "{\"type\":\"" + ID + ":assembly\",\"ingredients\":["
                + ingredients.formatted("minecraft:iron_ingot", 2) + "," + ingredients.formatted("minecraft:redstone", 1)
                + "," + ingredients.formatted("minecraft:quartz", 1) + "],\"result\":{\""
                + (policy.legacy() ? "item" : "id") + "\":\"minecraft:amethyst_shard\",\"count\":1}}\n");
    }

    private static void write(Path root, String relative, String content) throws Exception {
        Path output = root.resolve(relative).normalize();
        if (!output.startsWith(root)) throw new IllegalArgumentException("Fixture output escapes root");
        for (Path component = output; component != null; component = component.getParent()) {
            if (Files.isSymbolicLink(component)) throw new IllegalArgumentException("Symlink in fixture output");
        }
        Files.createDirectories(output.getParent());
        Files.writeString(output, content);
    }
}
