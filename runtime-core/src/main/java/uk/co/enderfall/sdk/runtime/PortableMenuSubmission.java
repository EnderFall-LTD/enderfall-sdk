package uk.co.enderfall.sdk.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable client draft values submitted with one declared portable-menu action. */
public record PortableMenuSubmission(String action, Map<String, String> inputs) {
    public PortableMenuSubmission {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(inputs, "inputs");
        inputs = Map.copyOf(new LinkedHashMap<>(inputs));
    }
}
