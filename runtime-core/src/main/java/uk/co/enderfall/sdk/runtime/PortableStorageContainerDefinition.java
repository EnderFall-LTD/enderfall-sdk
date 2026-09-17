package uk.co.enderfall.sdk.runtime;

import java.util.Objects;
import uk.co.enderfall.sdk.api.ui.StorageContainerRef;
import uk.co.enderfall.sdk.api.ui.StorageContainerSpec;

/** Internal immutable definition consumed by target-native storage adapters. */
public record PortableStorageContainerDefinition(StorageContainerRef reference, StorageContainerSpec spec) {
    public PortableStorageContainerDefinition {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(spec, "spec");
    }
}
