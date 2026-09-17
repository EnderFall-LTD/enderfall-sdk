package uk.co.enderfall.sdk.bridge;

import java.util.Objects;

/** One centrally reviewed source transformed into a target-specific output file. */
record RuntimeSource(String canonicalRelativePath, String relativePath, byte[] content) {
    RuntimeSource {
        Objects.requireNonNull(canonicalRelativePath, "canonicalRelativePath");
        Objects.requireNonNull(relativePath, "relativePath");
        Objects.requireNonNull(content, "content");
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
