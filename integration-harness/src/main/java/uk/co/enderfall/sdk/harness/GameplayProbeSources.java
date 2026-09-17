package uk.co.enderfall.sdk.harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import uk.co.enderfall.sdk.bridge.model.MinecraftAbi;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;

/** One test-only native driver, with only reviewed Minecraft input/GUI ABI substitutions. */
final class GameplayProbeSources {
    private GameplayProbeSources() { }

    static String render(String target) throws IOException {
        var spec = TargetCatalog.standard().require(target);
        boolean modern = spec.minecraftAbi() == MinecraftAbi.V26_2;
        String source;
        try (var input = GameplayProbeSources.class.getResourceAsStream("/gameplay/NativeGameplayClient.java.template")) {
            if (input == null) throw new IOException("Missing native gameplay probe template");
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        source = source.replace("@SCREEN@", modern ? "client.gui.screen()" : "client.screen")
                .replace("@CONNECTION_OBSERVER@", spec.minecraftAbi() == MinecraftAbi.V1_20_1
                        ? "LegacyConnectionDiagnostics.install(context);" : "")
                .replace("@PRESS_BUTTON@", modern
                        ? "button.onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0));" : "button.onPress();")
                .replace("@CLICK_TYPE@", modern ? "ContainerInput" : "ClickType")
                .replace("@CLICK_METHOD@", modern ? "handleContainerInput" : "handleInventoryMouseClick")
                // Legacy FML deprecates the global vanilla item registry. Use the stack's
                // registered holder on every 1.20.1 loader, without a loader-specific driver.
                .replace("@ITEM_ID@", spec.minecraftAbi() == MinecraftAbi.V1_20_1
                        ? "stack.getItemHolder().unwrapKey().orElseThrow().location().toString()"
                        : "BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()");
        if (source.contains("@SCREEN@") || source.contains("@CLICK_") || source.contains("@PRESS_BUTTON@")
                || source.contains("@ITEM_ID@") || source.contains("@CONNECTION_OBSERVER@")) {
            throw new IOException("Unresolved gameplay probe substitution");
        }
        return source;
    }

    static void write(Path workspace, String target) throws IOException {
        String source = render(target); // Validate the catalog ID before using it as a path.
        Path output = workspace.resolve("src/target").resolve(target)
                .resolve("java/uk/co/enderfall/sdk/testmod/NativeGameplayClient.java");
        Files.createDirectories(output.getParent());
        Files.writeString(output, source, StandardCharsets.UTF_8);
        if (TargetCatalog.standard().require(target).minecraftAbi() == MinecraftAbi.V1_20_1) {
            try (var input = GameplayProbeSources.class.getResourceAsStream(
                    "/gameplay/LegacyConnectionDiagnostics.java.template")) {
                if (input == null) throw new IOException("Missing legacy connection observer template");
                Files.writeString(output.resolveSibling("LegacyConnectionDiagnostics.java"),
                        new String(input.readAllBytes(), StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            }
        }
    }
}
