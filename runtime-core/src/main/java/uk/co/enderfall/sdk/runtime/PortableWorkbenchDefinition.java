package uk.co.enderfall.sdk.runtime;

import java.util.Objects;
import java.util.function.Consumer;
import uk.co.enderfall.sdk.api.ui.WorkbenchRef;
import uk.co.enderfall.sdk.api.ui.WorkbenchSpec;

/** Internal definition consumed by native workbench menu adapters. */
public record PortableWorkbenchDefinition(WorkbenchRef reference, WorkbenchSpec spec,
                                          Consumer<PortableWorkbenchCraft> craftListener) {
    public PortableWorkbenchDefinition {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(craftListener, "craftListener");
    }
}
