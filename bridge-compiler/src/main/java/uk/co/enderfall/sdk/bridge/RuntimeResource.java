package uk.co.enderfall.sdk.bridge;

import java.util.Objects;

/** One UTF-8 text resource emitted at a safe target-relative path. */
record RuntimeResource(String relativePath, String content) {
    RuntimeResource {
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(content, "content");
        if (relativePath.isBlank()) {
            throw new IllegalArgumentException("Runtime resource path cannot be blank");
        }
    }
}
