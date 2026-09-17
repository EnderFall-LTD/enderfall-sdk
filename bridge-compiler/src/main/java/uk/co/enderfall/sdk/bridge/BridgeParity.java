package uk.co.enderfall.sdk.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/** Read-only verifier for generated trees and removal of forbidden handwritten roots. */
public final class BridgeParity {
    /** One expected/generated directory comparison. */
    public record DirectoryPair(Path expected, Path actual) {
        /** Validates pair paths. */
        public DirectoryPair {
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(actual, "actual");
        }
    }

    /** One expected/generated regular-file comparison, such as a final remapped JAR. */
    public record FilePair(Path expected, Path actual) {
        /** Validates pair paths. */
        public FilePair {
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(actual, "actual");
        }
    }

    /** Immutable parity verification result. */
    public record Result(int comparedFiles, List<String> violations) {
        /** Creates an immutable result. */
        public Result {
            if (comparedFiles < 0) {
                throw new IllegalArgumentException("comparedFiles cannot be negative");
            }
            violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
        }

        /** Returns whether every comparison matched and all forbidden roots were absent. */
        public boolean matches() {
            return violations.isEmpty();
        }
    }

    /** Verifies all paths without modifying them. */
    public Result verify(List<Path> forbiddenRoots, List<DirectoryPair> pairs) {
        return verify(forbiddenRoots, pairs, List.of());
    }

    /** Verifies all directory and regular-file pairs without modifying them. */
    public Result verify(
            List<Path> forbiddenRoots,
            List<DirectoryPair> directoryPairs,
            List<FilePair> filePairs) {
        Objects.requireNonNull(forbiddenRoots, "forbiddenRoots");
        Objects.requireNonNull(directoryPairs, "directoryPairs");
        Objects.requireNonNull(filePairs, "filePairs");
        List<String> violations = new ArrayList<>();
        for (Path forbiddenRoot : forbiddenRoots) {
            Objects.requireNonNull(forbiddenRoot, "forbiddenRoots cannot contain null");
            Path normalized = forbiddenRoot.toAbsolutePath().normalize();
            if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
                violations.add("forbidden handwritten root still exists: " + normalized);
            }
        }

        int comparedFiles = 0;
        for (int index = 0; index < directoryPairs.size(); index++) {
            DirectoryPair pair = Objects.requireNonNull(
                    directoryPairs.get(index), "directoryPairs cannot contain null");
            Path expectedRoot = pair.expected().toAbsolutePath().normalize();
            Path actualRoot = pair.actual().toAbsolutePath().normalize();
            String label = "comparison " + (index + 1) + " [" + expectedRoot + " -> " + actualRoot + "]";
            Map<String, FileDigest> expected = scan(expectedRoot, label + " expected", violations);
            Map<String, FileDigest> actual = scan(actualRoot, label + " actual", violations);
            comparedFiles += expected.size();
            compare(label, expected, actual, violations);
        }
        for (int index = 0; index < filePairs.size(); index++) {
            FilePair pair = Objects.requireNonNull(filePairs.get(index), "filePairs cannot contain null");
            Path expected = pair.expected().toAbsolutePath().normalize();
            Path actual = pair.actual().toAbsolutePath().normalize();
            String label = "file comparison " + (index + 1) + " [" + expected + " -> " + actual + "]";
            FileDigest expectedDigest = digestRegularFile(expected, label + " expected", violations);
            FileDigest actualDigest = digestRegularFile(actual, label + " actual", violations);
            if (expectedDigest != null) {
                comparedFiles++;
            }
            if (expectedDigest != null && actualDigest != null && !expectedDigest.equals(actualDigest)) {
                violations.add(label + " content mismatch: expected SHA-256 " + expectedDigest.sha256()
                        + " (" + expectedDigest.size() + " bytes), found " + actualDigest.sha256()
                        + " (" + actualDigest.size() + " bytes)");
            }
        }
        violations.sort(String::compareTo);
        return new Result(comparedFiles, violations);
    }

    private static Map<String, FileDigest> scan(Path root, String label, List<String> violations) {
        Map<String, FileDigest> files = new LinkedHashMap<>();
        if (Files.isSymbolicLink(root)) {
            violations.add(label + " root cannot be a symbolic link: " + root);
            return files;
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            violations.add(label + " root does not exist or is not a directory: " + root);
            return files;
        }

        List<Path> entries;
        try (Stream<Path> stream = Files.walk(root)) {
            entries = stream.sorted(Comparator.comparing(path -> portable(root.relativize(path)))).toList();
        } catch (IOException | UncheckedIOException exception) {
            violations.add(label + " could not be inspected: " + exception.getMessage());
            return files;
        }

        Map<String, String> foldedPaths = new HashMap<>();
        for (Path entry : entries) {
            if (entry.equals(root)) {
                continue;
            }
            String relative = portable(root.relativize(entry));
            if (Files.isSymbolicLink(entry)) {
                violations.add(label + " contains forbidden symbolic link: " + relative);
                continue;
            }
            if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                violations.add(label + " contains unsupported filesystem entry: " + relative);
                continue;
            }
            String folded = relative.toLowerCase(Locale.ROOT);
            String previous = foldedPaths.putIfAbsent(folded, relative);
            if (previous != null) {
                violations.add(label + " contains duplicate or case-colliding files '" + previous
                        + "' and '" + relative + "'");
                continue;
            }
            try {
                files.put(relative, digest(entry));
            } catch (IOException exception) {
                violations.add(label + " could not read '" + relative + "': " + exception.getMessage());
            }
        }
        return files;
    }

    private static FileDigest digestRegularFile(Path path, String label, List<String> violations) {
        if (Files.isSymbolicLink(path)) {
            violations.add(label + " file cannot be a symbolic link: " + path);
            return null;
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            violations.add(label + " file does not exist or is not a regular file: " + path);
            return null;
        }
        try {
            return digest(path);
        } catch (IOException exception) {
            violations.add(label + " file could not be read: " + exception.getMessage());
            return null;
        }
    }

    private static void compare(
            String label,
            Map<String, FileDigest> expected,
            Map<String, FileDigest> actual,
            List<String> violations) {
        for (Map.Entry<String, FileDigest> entry : expected.entrySet()) {
            FileDigest actualDigest = actual.get(entry.getKey());
            if (actualDigest == null) {
                violations.add(label + " is missing generated file '" + entry.getKey() + "'");
            } else if (!entry.getValue().equals(actualDigest)) {
                violations.add(label + " content mismatch for '" + entry.getKey() + "': expected SHA-256 "
                        + entry.getValue().sha256() + " (" + entry.getValue().size() + " bytes), found "
                        + actualDigest.sha256() + " (" + actualDigest.size() + " bytes)");
            }
        }
        for (String path : actual.keySet()) {
            if (!expected.containsKey(path)) {
                violations.add(label + " has unexpected generated file '" + path + "'");
            }
        }
    }

    private static FileDigest digest(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Every Java 17 runtime must provide SHA-256", exception);
        }
        long size = 0;
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                    size += read;
                }
            }
        }
        return new FileDigest(HexFormat.of().formatHex(digest.digest()), size);
    }

    private static String portable(Path path) {
        return path.toString().replace('\\', '/');
    }

    private record FileDigest(String sha256, long size) {
    }
}
