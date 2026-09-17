package uk.co.enderfall.sdk.bridge;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.MinecraftVersion;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Shared native recipe-input list wrapper; legacy recipes use containers instead. */
final class WorkbenchInputEmitter {
    private WorkbenchInputEmitter() { }

    static List<RuntimeSource> emitIfPresent(TargetSpec target, Set<String> paths) throws BridgeGenerationException {
        if (!TargetCatalog.standard().require(target.id()).equals(target)) throw new BridgeGenerationException("Unreviewed recipe-input target " + target.id());
        if (target.minecraftVersion() == MinecraftVersion.V1_20_1) return List.of();
        boolean fabric = target.loaderAbi() == LoaderAbi.FABRIC;
        // NeoForge 26.2 emits its differently named input record with the recipe class.
        if (!fabric && target.minecraftVersion() == MinecraftVersion.V26_2) return List.of();
        String loader = fabric ? "fabric" : "neoforge";
        String prefix = fabric ? "Fabric" : "NeoForge";
        String path = "uk/co/enderfall/sdk/runtime/" + loader + "/v1_21_4/" + prefix + "WorkbenchInput.java";
        if (!paths.contains(path)) return List.of();
        return List.of(new RuntimeSource(path, path, INPUT.formatted(loader, prefix).getBytes(StandardCharsets.UTF_8)));
    }

    private static final String INPUT = """
            package uk.co.enderfall.sdk.runtime.%1$s.v1_21_4;

            import java.util.List;
            import net.minecraft.world.item.ItemStack;
            import net.minecraft.world.item.crafting.RecipeInput;

            record %2$sWorkbenchInput(List<ItemStack> items) implements RecipeInput {
                %2$sWorkbenchInput { items = List.copyOf(items); }
                @Override public ItemStack getItem(int index) { return items.get(index); }
                @Override public int size() { return items.size(); }
            }
            """;
}
