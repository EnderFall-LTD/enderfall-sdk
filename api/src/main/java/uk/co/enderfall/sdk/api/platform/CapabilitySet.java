package uk.co.enderfall.sdk.api.platform;

import java.util.Set;

/** Immutable view of capabilities implemented by the active target adapter. */
public interface CapabilitySet {
    boolean supports(Capability capability);

    Set<Capability> supported();

    default void require(Capability capability, String target) {
        if (!supports(capability)) {
            throw new UnsupportedCapabilityException(capability, target);
        }
    }
}
