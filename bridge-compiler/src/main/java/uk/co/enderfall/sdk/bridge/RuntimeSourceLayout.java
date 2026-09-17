package uk.co.enderfall.sdk.bridge;

import java.util.Set;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Declarations folded into another emitted source or absent from a target's native ABI. */
final class RuntimeSourceLayout {
    private RuntimeSourceLayout() { }

    static Set<String> omittedSourcePaths(TargetSpec target) throws BridgeGenerationException {
        NativePlatformPolicy policy = NativePlatformPolicy.forTarget(target);
        String root = "uk/co/enderfall/sdk/runtime/" + (policy.fabric() ? "fabric/v1_21_4/Fabric" : "neoforge/v1_21_4/NeoForge");
        return switch (policy) {
            case FABRIC_LEGACY -> Set.of(root + "RawPayload.java", root + "RecipeBinding.java", root + "WorkbenchInput.java");
            case LEGACY_FML -> Set.of(root + "RawPayload.java", root + "WorkbenchInput.java");
            case NEOFORGE_IDENTIFIER -> Set.of(root + "RecipeBinding.java", root + "WorkbenchInput.java");
            default -> Set.of();
        };
    }
}
