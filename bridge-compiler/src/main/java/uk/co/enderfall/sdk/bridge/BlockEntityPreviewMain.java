package uk.co.enderfall.sdk.bridge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates the isolated native block-entity development slice, never baseline runtime output. */
public final class BlockEntityPreviewMain {
    private BlockEntityPreviewMain() { }

    public static void main(String[] args) throws IOException, BridgeGenerationException {
        if (args.length != 2) throw new IllegalArgumentException("Expected target and output directory");
        var policy = BlockEntityNativePolicy.require(args[0]);
        String prefix = policy.prefix();
        String source = BlockEntityPreviewSources.emit(args[0]);
        Path output = Path.of(args[1]).resolve("uk/co/enderfall/sdk/runtime/blockentity/nativebridge/StoredBlockEntity.java");
        Files.createDirectories(output.getParent());
        Files.writeString(output, source, StandardCharsets.UTF_8);
        Files.writeString(output.resolveSibling("PortableShapeBlock.java"), BlockShapeSources.emit(args[0]), StandardCharsets.UTF_8);
        Path menu = Path.of(args[1]).resolve("uk/co/enderfall/sdk/runtime/" + policy.runtimePackage().replace('.', '/') + "/" + prefix + "PersistentWorkbenchMenu.java");
        Files.createDirectories(menu.getParent());
        Files.writeString(menu, policy.legacy() || policy.unobfuscated() ? ContainerWorkbenchMenuEmitter.persistentPreview(policy.fabric(), policy.legacy())
                : WorkbenchMenuEmitter.persistentPreview(policy.modernRecipes(), policy.fabric()), StandardCharsets.UTF_8);
        Files.writeString(menu.getParent().resolve(prefix + "TimedWorkbenchProcessor.java"),
                TimedWorkbenchProcessorSources.emit(policy), StandardCharsets.UTF_8);
        Files.writeString(menu.getParent().resolve(prefix + "TimedWorkbenchMenu.java"), TimedWorkbenchMenuSources.menu(policy), StandardCharsets.UTF_8);
        Files.writeString(menu.getParent().resolve(prefix + "TimedWorkbenchClient.java"), TimedWorkbenchMenuSources.client(policy), StandardCharsets.UTF_8);
        RuntimeSource platform = PlatformServiceEmitter.persistentPreview(args[0]);
        Path platformOutput = Path.of(args[1]).resolve(platform.relativePath());
        Files.createDirectories(platformOutput.getParent());
        Files.write(platformOutput, platform.content());
        Files.writeString(menu.getParent().resolve(prefix + "PersistentPreviewBootstrap.java"),
                PersistentPlatformSources.bootstrap(policy), StandardCharsets.UTF_8);
    }
}
