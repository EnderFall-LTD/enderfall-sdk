package uk.co.enderfall.sdk.api.block;

import java.util.Objects;
import uk.co.enderfall.sdk.api.annotation.Experimental;

/**
 * A verified, proposed block-property edit supplied immediately before it is committed.
 * Both states are immutable and use the block's declared portable schema.
 */
@Experimental("Transactional portable block-property tools")
public record BlockToolChange(
        BlockToolRef tool,
        int selectionIndex,
        int propertyCount,
        String propertyName,
        String previousValue,
        String value,
        PortableBlockState previousState,
        PortableBlockState proposedState) {
    public BlockToolChange {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(propertyName, "propertyName");
        Objects.requireNonNull(previousValue, "previousValue");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(previousState, "previousState");
        Objects.requireNonNull(proposedState, "proposedState");
        if (propertyCount < 1 || selectionIndex < 0 || selectionIndex >= propertyCount) {
            throw new IllegalArgumentException("Invalid tool-property selection");
        }
        if (previousValue.equals(value) || previousState.equals(proposedState)) {
            throw new IllegalArgumentException("A proposed tool change must change block state");
        }
    }
}
