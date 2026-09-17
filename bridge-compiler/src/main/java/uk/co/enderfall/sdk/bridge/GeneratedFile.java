package uk.co.enderfall.sdk.bridge;

import java.util.Objects;

/** One emitted file, represented independently of the machine-specific output root. */
public record GeneratedFile(OutputKind kind, String relativePath, long size) {
    /** Validates a generated-file description. */
    public GeneratedFile {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(relativePath, "relativePath");
        if (relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath cannot be blank");
        }
        if (size < 0) {
            throw new IllegalArgumentException("size cannot be negative");
        }
    }
}
