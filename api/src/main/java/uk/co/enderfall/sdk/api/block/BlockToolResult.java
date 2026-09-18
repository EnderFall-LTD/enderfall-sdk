package uk.co.enderfall.sdk.api.block;

import java.util.Objects;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/** Result of selecting or cycling one declared property with a portable block tool. */
@Experimental("Portable block-property tools")
public record BlockToolResult(
        BlockToolRef tool,
        int selectionIndex,
        int propertyCount,
        String propertyName,
        String previousValue,
        String value,
        boolean changed) {
    public BlockToolResult {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(propertyName, "propertyName");
        Objects.requireNonNull(previousValue, "previousValue");
        Objects.requireNonNull(value, "value");
        if (propertyCount < 1 || selectionIndex < 0 || selectionIndex >= propertyCount) {
            throw new IllegalArgumentException("Invalid tool-property selection");
        }
        if (changed == previousValue.equals(value)) {
            throw new IllegalArgumentException("Tool change flag does not match its values");
        }
    }
}
