package uk.co.enderfall.sdk.bridge;

import java.nio.file.Path;
import java.util.Objects;

/** Inputs for one isolated target generation. */
public record GenerationRequest(
        String targetId,
        Path canonicalRoot,
        Path outputSources,
        Path outputResources,
        boolean persistence) {

    /** Creates a baseline generation request, preserving historical parity. */
    public GenerationRequest(String targetId, Path canonicalRoot, Path outputSources, Path outputResources) {
        this(targetId, canonicalRoot, outputSources, outputResources, false);
    }

    /** Validates non-null request properties. Filesystem validation happens before generation. */
    public GenerationRequest {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(canonicalRoot, "canonicalRoot");
        Objects.requireNonNull(outputSources, "outputSources");
        Objects.requireNonNull(outputResources, "outputResources");
    }
}
