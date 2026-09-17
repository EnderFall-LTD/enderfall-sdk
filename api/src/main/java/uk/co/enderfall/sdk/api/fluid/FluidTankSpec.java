package uk.co.enderfall.sdk.api.fluid;

import java.util.Objects;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/** Named block-owned tank definition. Capacity is measured in SDK fluid units. */
@Experimental
public record FluidTankSpec(String name, long capacity, java.util.Map<FluidFace, FluidPortMode> ports) {
    /** No external access unless ports are explicitly configured. */
    public FluidTankSpec(String name, long capacity) { this(name, capacity, java.util.Map.of()); }

    public FluidTankSpec {
        Objects.requireNonNull(name, "name");
        if (!name.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("Invalid tank name");
        if (capacity <= 0) throw new IllegalArgumentException("Tank capacity must be positive");
        ports = java.util.Map.copyOf(ports);
    }

    public FluidPortMode port(FluidFace face) {
        return ports.getOrDefault(Objects.requireNonNull(face), FluidPortMode.CLOSED);
    }
}
