package uk.co.enderfall.sdk.api.blockentity;

import java.util.Objects;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/** A named, saved integer such as machine progress or stored energy. Names are save-format keys. */
@Experimental("Block-entity persistence foundation; native registration is not available yet")
public record BlockEntityInt(String name, int defaultValue, int minimum, int maximum) {
    public BlockEntityInt {
        Objects.requireNonNull(name, "name");
        if (!name.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("Invalid block-entity field name: " + name);
        }
        if (minimum > maximum || defaultValue < minimum || defaultValue > maximum) {
            throw new IllegalArgumentException("Invalid bounds/default for " + name);
        }
    }

    public int validate(int value) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Value outside bounds for " + name);
        }
        return value;
    }
}
