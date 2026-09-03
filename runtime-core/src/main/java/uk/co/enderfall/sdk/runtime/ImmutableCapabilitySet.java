package uk.co.enderfall.sdk.runtime;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import uk.co.enderfall.sdk.api.platform.Capability;
import uk.co.enderfall.sdk.api.platform.CapabilitySet;

public final class ImmutableCapabilitySet implements CapabilitySet {
    private final Set<Capability> capabilities;

    public ImmutableCapabilitySet(Set<Capability> capabilities) {
        this.capabilities = Collections.unmodifiableSet(capabilities.isEmpty()
                ? EnumSet.noneOf(Capability.class)
                : EnumSet.copyOf(capabilities));
    }

    public static ImmutableCapabilitySet all() {
        return new ImmutableCapabilitySet(EnumSet.allOf(Capability.class));
    }

    @Override
    public boolean supports(Capability capability) {
        return capabilities.contains(capability);
    }

    @Override
    public Set<Capability> supported() {
        return capabilities;
    }
}
