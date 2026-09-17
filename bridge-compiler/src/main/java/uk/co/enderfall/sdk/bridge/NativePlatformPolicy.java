package uk.co.enderfall.sdk.bridge;

import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Reviewed combinations of registry, event, identifier, and payload APIs. */
enum NativePlatformPolicy {
    FABRIC_LEGACY, FABRIC_UNKEYED, FABRIC_KEYED, FABRIC_IDENTIFIER,
    LEGACY_FML, NEOFORGE, NEOFORGE_IDENTIFIER;

    static NativePlatformPolicy forTarget(TargetSpec target) throws BridgeGenerationException {
        if (!TargetCatalog.standard().require(target.id()).equals(target)) {
            throw new BridgeGenerationException("Unreviewed platform-service target " + target.id());
        }
        if (target.loaderAbi() == LoaderAbi.LEGACY_FML) return LEGACY_FML;
        if (target.loaderAbi() == LoaderAbi.MODERN_NEOFORGE) {
            return target.minecraftAbi() == uk.co.enderfall.sdk.bridge.model.MinecraftAbi.V26_2
                    ? NEOFORGE_IDENTIFIER : NEOFORGE;
        }
        return switch (target.minecraftAbi()) {
            case V1_20_1 -> FABRIC_LEGACY;
            case V1_21_1 -> FABRIC_UNKEYED;
            case V1_21_4 -> FABRIC_KEYED;
            case V26_2 -> FABRIC_IDENTIFIER;
        };
    }

    boolean fabric() {
        return this == FABRIC_LEGACY || this == FABRIC_UNKEYED || this == FABRIC_KEYED || this == FABRIC_IDENTIFIER;
    }

    boolean legacyChannel() { return this == FABRIC_LEGACY || this == LEGACY_FML; }
    boolean identifier() { return this == FABRIC_IDENTIFIER || this == NEOFORGE_IDENTIFIER; }
}
