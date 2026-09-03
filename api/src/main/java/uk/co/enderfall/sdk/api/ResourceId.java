package uk.co.enderfall.sdk.api;

import java.util.Objects;
import java.util.regex.Pattern;

/** A loader-neutral namespaced identifier. */
public record ResourceId(String namespace, String path) implements Comparable<ResourceId> {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9/._-]+");

    public ResourceId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid resource namespace: " + namespace);
        }
        if (path.isEmpty() || !PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid resource path: " + path);
        }
    }

    public static ResourceId of(String namespace, String path) {
        return new ResourceId(namespace, path);
    }

    public static ResourceId parse(String value) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1 || value.indexOf(':', separator + 1) >= 0) {
            throw new IllegalArgumentException("Resource ID must be namespace:path: " + value);
        }
        return new ResourceId(value.substring(0, separator), value.substring(separator + 1));
    }

    @Override
    public int compareTo(ResourceId other) {
        int namespaceComparison = namespace.compareTo(other.namespace);
        return namespaceComparison != 0 ? namespaceComparison : path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return namespace + ':' + path;
    }
}
