package uk.co.enderfall.sdk.bridge;

import java.util.List;
import java.util.Objects;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Stable summary of one successful generation. */
public record GenerationResult(TargetSpec target, List<GeneratedFile> files, String sha256) {
    /** Creates an immutable generation result. */
    public GenerationResult {
        Objects.requireNonNull(target, "target");
        files = List.copyOf(Objects.requireNonNull(files, "files"));
        Objects.requireNonNull(sha256, "sha256");
    }
}
