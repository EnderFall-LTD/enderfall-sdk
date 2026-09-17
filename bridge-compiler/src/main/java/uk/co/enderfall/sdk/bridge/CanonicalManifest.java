package uk.co.enderfall.sdk.bridge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict parser and verifier for the frozen canonical runtime file manifest. */
final class CanonicalManifest {
    private static final String FILE_NAME = "MANIFEST.sha256";
    private static final Pattern ENTRY = Pattern.compile(
            "^([0-9a-f]{64})  (src/(?:canonical|neoforge)/(?:java|resources)/.+)$");

    private CanonicalManifest() {
    }

    static void validate(Path canonicalRoot, Map<String, byte[]> actualFiles) throws BridgeGenerationException {
        Path manifest = canonicalRoot.resolve(FILE_NAME).normalize();
        if (Files.isSymbolicLink(manifest)) {
            throw new BridgeGenerationException("Canonical manifest cannot be a symbolic link: " + manifest);
        }
        if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)) {
            throw new BridgeGenerationException("Canonical manifest does not exist or is not a file: " + manifest);
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BridgeGenerationException(
                    "Could not read canonical manifest " + manifest + ": " + exception.getMessage(), exception);
        }

        Map<String, ManifestEntry> entries = new HashMap<>();
        Map<String, String> caseInsensitivePaths = new HashMap<>();
        List<String> violations = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            Matcher matcher = ENTRY.matcher(line);
            if (!matcher.matches()) {
                violations.add("line " + (index + 1) + " has invalid '<sha256>  <path>' syntax");
                continue;
            }
            String hash = matcher.group(1);
            String relativePath = matcher.group(2);
            String pathProblem = validateRelativePath(canonicalRoot, relativePath);
            if (pathProblem != null) {
                violations.add("line " + (index + 1) + " " + pathProblem);
                continue;
            }
            String folded = relativePath.toLowerCase(Locale.ROOT);
            String previousCase = caseInsensitivePaths.putIfAbsent(folded, relativePath);
            if (previousCase != null) {
                violations.add("duplicate or case-colliding path '" + relativePath + "' (first '"
                        + previousCase + "')");
                continue;
            }
            entries.put(relativePath, new ManifestEntry(hash, relativePath));
        }
        if (entries.isEmpty()) {
            violations.add("manifest contains no valid canonical files");
        }

        Map<String, String> actualCaseInsensitive = new HashMap<>();
        for (String path : actualFiles.keySet()) {
            String folded = path.toLowerCase(Locale.ROOT);
            String previous = actualCaseInsensitive.putIfAbsent(folded, path);
            if (previous != null) {
                violations.add("canonical tree has duplicate or case-colliding paths '" + previous
                        + "' and '" + path + "'");
            }
        }

        for (ManifestEntry entry : entries.values()) {
            byte[] actual = actualFiles.get(entry.path());
            if (actual == null) {
                violations.add("manifest lists missing canonical file '" + entry.path() + "'");
            } else {
                String actualHash = hash(actual);
                if (!actualHash.equals(entry.sha256())) {
                    violations.add("SHA-256 mismatch for '" + entry.path() + "': expected "
                            + entry.sha256() + " but found " + actualHash);
                }
            }
        }
        for (String path : actualFiles.keySet()) {
            if (!entries.containsKey(path)) {
                violations.add("canonical file is not listed in manifest: '" + path + "'");
            }
        }

        if (!violations.isEmpty()) {
            violations.sort(String::compareTo);
            throw new BridgeGenerationException(
                    "Canonical manifest validation failed: " + String.join("; ", violations));
        }
    }

    private static String validateRelativePath(Path canonicalRoot, String relativePath) {
        if (relativePath.indexOf('\\') >= 0) {
            return "contains a backslash instead of a portable '/' path: '" + relativePath + "'";
        }
        final Path path;
        try {
            path = Path.of(relativePath);
        } catch (InvalidPathException exception) {
            return "contains an invalid path: '" + relativePath + "'";
        }
        Path normalized = path.normalize();
        String portableNormalized = normalized.toString().replace('\\', '/');
        if (path.isAbsolute() || normalized.startsWith("..") || !portableNormalized.equals(relativePath)) {
            return "contains an unsafe or non-normalized path: '" + relativePath + "'";
        }
        Path resolved = canonicalRoot.resolve(normalized).normalize();
        if (!resolved.startsWith(canonicalRoot)) {
            return "escapes the canonical root: '" + relativePath + "'";
        }
        return null;
    }

    private static String hash(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Every Java 17 runtime must provide SHA-256", exception);
        }
    }

    private record ManifestEntry(String sha256, String path) {
    }
}
