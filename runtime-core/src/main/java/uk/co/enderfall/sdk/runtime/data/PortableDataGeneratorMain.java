package uk.co.enderfall.sdk.runtime.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumSet;
import uk.co.enderfall.sdk.api.ResourceId;
import uk.co.enderfall.sdk.api.command.CommandSpec;
import uk.co.enderfall.sdk.api.network.PacketDirection;
import uk.co.enderfall.sdk.api.platform.Capability;
import uk.co.enderfall.sdk.api.platform.CapabilitySet;
import uk.co.enderfall.sdk.api.platform.Environment;
import uk.co.enderfall.sdk.api.platform.Loader;
import uk.co.enderfall.sdk.api.platform.MinecraftVersion;
import uk.co.enderfall.sdk.api.platform.PlatformInfo;
import uk.co.enderfall.sdk.api.registry.BlockSpec;
import uk.co.enderfall.sdk.api.registry.CreativeTabSpec;
import uk.co.enderfall.sdk.api.registry.ItemSpec;
import uk.co.enderfall.sdk.api.recipe.WorkbenchRecipeTypeRef;
import uk.co.enderfall.sdk.runtime.ImmutableCapabilitySet;
import uk.co.enderfall.sdk.runtime.PayloadReceiver;
import uk.co.enderfall.sdk.runtime.PlatformAdapter;
import uk.co.enderfall.sdk.runtime.RuntimeModBootstrap;
import uk.co.enderfall.sdk.runtime.PortableWorkbenchDefinition;

/** Standalone entrypoint used by the Gradle plugin to execute portable data definitions. */
public final class PortableDataGeneratorMain {
    private PortableDataGeneratorMain() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 5) {
            throw new IllegalArgumentException(
                    "Expected: <mod-id> <entrypoint> <minecraft-version> <loader> <output-directory>");
        }
        String modId = arguments[0];
        String entrypoint = arguments[1];
        MinecraftVersion minecraftVersion = new MinecraftVersion(arguments[2]);
        Loader loader = Loader.valueOf(arguments[3].toUpperCase(java.util.Locale.ROOT));
        Path output = Path.of(arguments[4]).toAbsolutePath().normalize();
        DataGenerationAdapter adapter = new DataGenerationAdapter(
                new DataGenerationPlatformInfo(minecraftVersion, loader, modId),
                output.getParent().resolve(".config"));
        var context = RuntimeModBootstrap.initialize(modId, entrypoint, "", adapter,
                Thread.currentThread().getContextClassLoader());
        JsonDataGenerationContext data = new JsonDataGenerationContext(minecraftVersion, modId);
        context.runtimeDataGeneration().generate(data);
        recreateDirectory(output);
        data.writeTo(output);
        context.logger().info("Generated {} portable resources for {}-{} into {}",
                data.resources().size(), minecraftVersion, loader.name().toLowerCase(java.util.Locale.ROOT), output);
    }

    private static void recreateDirectory(Path output) throws IOException {
        if (output.getParent() == null) {
            throw new IOException("Refusing to use a filesystem root as the data-generation output: " + output);
        }
        if (Files.exists(output)) {
            try (var paths = Files.walk(output)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(output);
    }

    private record DataGenerationPlatformInfo(MinecraftVersion minecraftVersion, Loader loader, String consumerModId)
            implements PlatformInfo {
        @Override public Environment environment() { return Environment.DEDICATED_SERVER; }
        @Override public boolean isModLoaded(String modId) {
            return modId.equals("enderfall_sdk") || modId.equals(consumerModId);
        }
        @Override public java.util.Optional<String> modVersion(String modId) {
            return isModLoaded(modId) ? java.util.Optional.of("data-generation") : java.util.Optional.empty();
        }
    }

    private static final class DataGenerationAdapter implements PlatformAdapter {
        private final PlatformInfo platformInfo;
        private final Path configDirectory;
        private final CapabilitySet capabilities = new ImmutableCapabilitySet(EnumSet.complementOf(
                EnumSet.of(Capability.MIXED_LOADER_NETWORKING)));

        private DataGenerationAdapter(PlatformInfo platformInfo, Path configDirectory) {
            this.platformInfo = platformInfo;
            this.configDirectory = configDirectory;
        }

        @Override public PlatformInfo platformInfo() { return platformInfo; }
        @Override public CapabilitySet capabilities() { return capabilities; }
        @Override public Path commonConfigDirectory() { return configDirectory; }
        @Override public Path serverConfigDirectory() { return configDirectory.resolve("serverconfig"); }
        @Override public void registerItem(ResourceId id, ItemSpec spec) { }
        @Override public void registerBlock(ResourceId id, BlockSpec spec, ItemSpec blockItemSpec) { }
        @Override public void registerCreativeTab(ResourceId id, CreativeTabSpec spec) { }
        @Override public void registerCommand(CommandSpec command) { }
        @Override public void registerWorkbenchRecipeType(WorkbenchRecipeTypeRef recipeType) { }
        @Override public void registerWorkbench(PortableWorkbenchDefinition definition) { }
        @Override public void registerPayload(ResourceId id, PacketDirection direction, int maximumBytes,
                                              PayloadReceiver receiver) { }

        @Override
        public void sendToServer(ResourceId id, byte[] payload) {
            throw new IllegalStateException("Networking is unavailable during data generation");
        }

        @Override
        public void sendToPlayer(java.util.UUID playerId, ResourceId id, byte[] payload) {
            throw new IllegalStateException("Networking is unavailable during data generation");
        }

        @Override
        public void sendToAll(ResourceId id, byte[] payload) {
            throw new IllegalStateException("Networking is unavailable during data generation");
        }
    }
}
