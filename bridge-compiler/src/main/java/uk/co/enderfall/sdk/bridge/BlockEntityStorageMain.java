package uk.co.enderfall.sdk.bridge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** SDK-only native storage generation; does not imply that a target's menus/platform are ready. */
public final class BlockEntityStorageMain {
    private BlockEntityStorageMain() { }
    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Expected target and output directory");
        String source = BlockEntityPreviewSources.emit(args[0]);
        Path output = Path.of(args[1]).resolve("uk/co/enderfall/sdk/runtime/blockentity/nativebridge/StoredBlockEntity.java");
        Files.createDirectories(output.getParent());
        Files.writeString(output, source, StandardCharsets.UTF_8);
        Files.writeString(output.resolveSibling("PortableShapeBlock.java"), BlockShapeSources.emit(args[0]), StandardCharsets.UTF_8);
    }
}
