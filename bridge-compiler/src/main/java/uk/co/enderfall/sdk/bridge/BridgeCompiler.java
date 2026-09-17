package uk.co.enderfall.sdk.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.ModRuntimeMetadata;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Validates declarations and emits target runtimes from shared native operations. */
public final class BridgeCompiler {
    private static final Set<String> IMPLEMENTED_TARGETS = Set.of(
            "1.20.1-fabric", "1.20.1-forge", "1.20.1-neoforge",
            "1.21.1-fabric", "1.21.1-neoforge", "1.21.4-fabric", "1.21.4-neoforge", "26.2-fabric", "26.2-neoforge");
    private static final String FABRIC_PROFILE_PATH = "src/canonical";
    private static final String NEOFORGE_PROFILE_PATH = "src/neoforge";

    private final TargetCatalog catalog;
    private final ModRuntimeMetadata runtimeMetadata;

    /** Creates a compiler using the reviewed EnderFall target catalog. */
    public BridgeCompiler() {
        this(TargetCatalog.standard(), ModRuntimeMetadata.enderfallSdk());
    }

    /** Creates a compiler with an explicitly supplied validated catalog. */
    public BridgeCompiler(TargetCatalog catalog) {
        this(catalog, ModRuntimeMetadata.enderfallSdk());
    }

    /** Creates a compiler with explicitly supplied target and runtime metadata. */
    public BridgeCompiler(TargetCatalog catalog, ModRuntimeMetadata runtimeMetadata) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.runtimeMetadata = Objects.requireNonNull(runtimeMetadata, "runtimeMetadata");
    }

    /** Returns target IDs for which code emission is implemented in this compiler build. */
    public List<String> implementedTargetIds() {
        return IMPLEMENTED_TARGETS.stream().sorted().toList();
    }

    /**
     * Generates one target without overwriting existing files.
     *
     * @param request canonical input and isolated output roots
     * @return deterministic file list and content digest
     * @throws BridgeGenerationException if validation or I/O fails
     */
    public GenerationResult generate(GenerationRequest request) throws BridgeGenerationException {
        Objects.requireNonNull(request, "request");
        TargetSpec target;
        try {
            target = catalog.require(request.targetId());
        } catch (IllegalArgumentException exception) {
            throw new BridgeGenerationException(exception.getMessage(), exception);
        }
        if (!IMPLEMENTED_TARGETS.contains(target.id())) {
            throw new BridgeGenerationException(
                    "Target '" + target.id() + "' is known, but bridge generation is not implemented yet. "
                            + "Implemented targets: " + String.join(", ", implementedTargetIds())
                            + ". Known targets: " + String.join(", ", catalog.targetIds()));
        }
        TargetSpec reviewedTarget = TargetCatalog.standard().require(target.id());
        if (!target.equals(reviewedTarget)) {
            throw new BridgeGenerationException(
                    "Target '" + target.id() + "' differs from its reviewed implemented specification");
        }

        Path canonicalRoot = normalize(request.canonicalRoot());
        Path outputSources = normalize(request.outputSources());
        Path outputResources = normalize(request.outputResources());
        String profilePath = switch (target.loaderAbi()) {
            case FABRIC -> FABRIC_PROFILE_PATH;
            case MODERN_NEOFORGE, LEGACY_FML -> NEOFORGE_PROFILE_PATH;
        };
        Path sourceRoot = canonicalRoot.resolve(profilePath).resolve("java").normalize();
        Path resourceRoot = canonicalRoot.resolve(profilePath).resolve("resources").normalize();

        requireInputDirectory(canonicalRoot, "canonical root");
        requireCanonicalSubdirectory(canonicalRoot, sourceRoot, "canonical Java root");
        requireCanonicalSubdirectory(canonicalRoot, resourceRoot, "canonical resource root");
        rejectOverlap(canonicalRoot, outputSources, "canonical root", "source output");
        rejectOverlap(canonicalRoot, outputResources, "canonical root", "resource output");
        rejectOverlap(outputSources, outputResources, "source output", "resource output");
        rejectOutputSymlink(outputSources, "source output");
        rejectOutputSymlink(outputResources, "resource output");

        List<PlannedFile> canonicalFilesToPlan = new ArrayList<>();
        canonicalFilesToPlan.addAll(scan(canonicalRoot, sourceRoot, outputSources, OutputKind.SOURCE));
        canonicalFilesToPlan.addAll(scan(canonicalRoot, resourceRoot, outputResources, OutputKind.RESOURCE));
        if (canonicalFilesToPlan.isEmpty()) {
            throw new BridgeGenerationException(
                    "Canonical runtime contains no files under " + sourceRoot + " or " + resourceRoot);
        }

        Map<String, byte[]> canonicalFiles = scanCanonicalManifestFiles(canonicalRoot);
        CanonicalManifest.validate(canonicalRoot, canonicalFiles);

        Map<String, byte[]> canonicalSources = new HashMap<>();
        for (PlannedFile file : canonicalFilesToPlan) {
            if (file.kind() == OutputKind.SOURCE) {
                canonicalSources.put(file.portableRelativePath(), file.bytes());
            }
        }
        List<RuntimeSource> emittedSources = SharedRuntimeSources.emit(target, canonicalSources.keySet(), request.persistence());
        Set<String> replacedSourcePaths = emittedSources.stream()
                .map(RuntimeSource::canonicalRelativePath)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> omittedSourcePaths = RuntimeSourceLayout.omittedSourcePaths(target);
        List<String> unhandledSources = canonicalSources.keySet().stream()
                .filter(path -> !replacedSourcePaths.contains(path) && !omittedSourcePaths.contains(path))
                .sorted().toList();
        if (!unhandledSources.isEmpty()) {
            throw new BridgeGenerationException("Target '" + target.id()
                    + "' has declarations without a shared emitter: " + String.join(", ", unhandledSources)
                    + ". Canonical source copying is forbidden; implement a reviewed emitter first.");
        }
        List<RuntimeResource> emittedResources = switch (target.loaderAbi()) {
            case FABRIC -> FabricRuntimeResourceEmitter.emit(target, runtimeMetadata);
            case MODERN_NEOFORGE -> NeoForgeRuntimeResourceEmitter.emit(target, runtimeMetadata);
            case LEGACY_FML -> LegacyFmlResourceEmitter.emit(target, runtimeMetadata);
        };
        Set<String> omittedResourcePaths = target.loaderAbi() == LoaderAbi.LEGACY_FML
                ? Set.of("META-INF/neoforge.mods.toml") : Set.of();
        Set<String> emittedResourcePaths = emittedResources.stream()
                .map(RuntimeResource::relativePath)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<PlannedFile> planned = new ArrayList<>(canonicalFilesToPlan);
        planned.removeIf(file -> file.kind() == OutputKind.SOURCE
                && (replacedSourcePaths.contains(file.portableRelativePath())
                        || omittedSourcePaths.contains(file.portableRelativePath())));
        planned.removeIf(file -> file.kind() == OutputKind.RESOURCE
                && (emittedResourcePaths.contains(file.portableRelativePath())
                        || omittedResourcePaths.contains(file.portableRelativePath())));
        planned.addAll(planGeneratedSources(target, outputSources, emittedSources));
        planned.addAll(planGeneratedResources(outputResources, emittedResources));
        planned.sort(Comparator.comparing(PlannedFile::stableName));

        rejectDuplicateOutputs(planned);
        List<PlannedFile> missing = preflightOutputs(planned);
        MessageDigest digest = sha256();
        List<GeneratedFile> generated = new ArrayList<>(planned.size());
        try {
            for (PlannedFile file : missing) {
                publishMissing(file);
            }
            verifyCompletedOutputs(planned);
            for (PlannedFile file : planned) {
                updateDigest(digest, file);
                generated.add(new GeneratedFile(file.kind(), file.portableRelativePath(), file.bytes().length));
            }
        } catch (IOException | UncheckedIOException exception) {
            throw new BridgeGenerationException("Could not write generated bridge output: " + exception.getMessage(),
                    exception);
        }
        return new GenerationResult(target, generated, HexFormat.of().formatHex(digest.digest()));
    }

    private static List<PlannedFile> planGeneratedSources(
            TargetSpec target,
            Path outputRoot,
            List<RuntimeSource> sources) throws BridgeGenerationException {
        List<PlannedFile> files = new ArrayList<>(sources.size());
        for (RuntimeSource source : sources) {
            Path relative = Path.of(source.relativePath()).normalize();
            if (relative.isAbsolute() || relative.startsWith("..")
                    || !portable(relative).equals(source.relativePath())) {
                throw new BridgeGenerationException(
                        "Generated runtime source has an unsafe or non-normalized path: " + source.relativePath());
            }
            Path destination = outputRoot.resolve(relative).normalize();
            if (!destination.startsWith(outputRoot)) {
                throw new BridgeGenerationException(
                        "Generated runtime source escapes its destination root: " + source.relativePath());
            }
            rejectOutputPathComponents(outputRoot, destination.getParent());
            files.add(new PlannedFile(
                    OutputKind.SOURCE,
                    "typed-source-transform/" + target.id() + '/' + source.canonicalRelativePath(),
                    relative,
                    outputRoot,
                    destination,
                    source.content()));
        }
        return files;
    }

    private static List<PlannedFile> planGeneratedResources(
            Path outputRoot,
            List<RuntimeResource> resources) throws BridgeGenerationException {
        List<PlannedFile> files = new ArrayList<>(resources.size());
        for (RuntimeResource resource : resources) {
            Path relative = Path.of(resource.relativePath()).normalize();
            if (relative.isAbsolute() || relative.startsWith("..")
                    || !portable(relative).equals(resource.relativePath())) {
                throw new BridgeGenerationException(
                        "Generated runtime resource has an unsafe or non-normalized path: " + resource.relativePath());
            }
            Path destination = outputRoot.resolve(relative).normalize();
            if (!destination.startsWith(outputRoot)) {
                throw new BridgeGenerationException(
                        "Generated runtime resource escapes its destination root: " + resource.relativePath());
            }
            rejectOutputPathComponents(outputRoot, destination.getParent());
            files.add(new PlannedFile(
                    OutputKind.RESOURCE,
                    "typed-runtime-metadata/" + resource.relativePath(),
                    relative,
                    outputRoot,
                    destination,
                    resource.content().getBytes(StandardCharsets.UTF_8)));
        }
        return files;
    }

    private static Map<String, byte[]> scanCanonicalManifestFiles(Path canonicalRoot)
            throws BridgeGenerationException {
        Path inputRoot = canonicalRoot.resolve("src").normalize();
        requireCanonicalSubdirectory(canonicalRoot, inputRoot, "canonical profile root");
        Map<String, byte[]> files = new HashMap<>();
        try (Stream<Path> stream = Files.walk(inputRoot)) {
            for (Path entry : stream.sorted(Comparator.comparing(path -> portable(inputRoot.relativize(path)))).toList()) {
                if (entry.equals(inputRoot)) {
                    continue;
                }
                if (Files.isSymbolicLink(entry)) {
                    throw new BridgeGenerationException("Symbolic links are forbidden in canonical input: " + entry);
                }
                if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    throw new BridgeGenerationException("Unsupported canonical filesystem entry: " + entry);
                }
                files.put(portable(canonicalRoot.relativize(entry)), Files.readAllBytes(entry));
            }
        } catch (IOException | UncheckedIOException exception) {
            throw new BridgeGenerationException(
                    "Could not inspect canonical profile tree " + inputRoot + ": " + exception.getMessage(),
                    exception);
        }
        return files;
    }

    private static List<PlannedFile> scan(Path canonicalRoot, Path inputRoot, Path outputRoot, OutputKind kind)
            throws BridgeGenerationException {
        List<Path> entries;
        try (Stream<Path> stream = Files.walk(inputRoot)) {
            entries = stream.sorted(Comparator.comparing(path -> portable(inputRoot.relativize(path)))).toList();
        } catch (IOException | UncheckedIOException exception) {
            throw new BridgeGenerationException("Could not inspect canonical " + kind.name().toLowerCase(Locale.ROOT)
                    + " tree " + inputRoot + ": " + exception.getMessage(), exception);
        }

        List<PlannedFile> files = new ArrayList<>();
        for (Path entry : entries) {
            if (entry.equals(inputRoot)) {
                continue;
            }
            if (Files.isSymbolicLink(entry)) {
                throw new BridgeGenerationException("Symbolic links are forbidden in canonical input: " + entry);
            }
            if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                throw new BridgeGenerationException("Unsupported canonical filesystem entry: " + entry);
            }
            Path relative = inputRoot.relativize(entry).normalize();
            if (relative.isAbsolute() || relative.startsWith("..")) {
                throw new BridgeGenerationException("Canonical file escapes its source root: " + entry);
            }
            Path destination = outputRoot.resolve(relative).normalize();
            if (!destination.startsWith(outputRoot)) {
                throw new BridgeGenerationException("Generated output escapes its destination root: " + relative);
            }
            rejectOutputPathComponents(outputRoot, destination.getParent());
            try {
                files.add(new PlannedFile(
                        kind,
                        portable(canonicalRoot.relativize(entry)),
                        relative,
                        outputRoot,
                        destination,
                        Files.readAllBytes(entry)));
            } catch (IOException exception) {
                throw new BridgeGenerationException("Could not read canonical file " + entry + ": "
                        + exception.getMessage(), exception);
            }
        }
        return files;
    }

    private static void rejectDuplicateOutputs(List<PlannedFile> files) throws BridgeGenerationException {
        Map<String, PlannedFile> destinations = new HashMap<>();
        for (PlannedFile file : files) {
            String key = file.destination().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
            PlannedFile previous = destinations.putIfAbsent(key, file);
            if (previous != null) {
                throw new BridgeGenerationException("Duplicate generated output '" + file.destination()
                        + "' from " + previous.portableRelativePath() + " and " + file.portableRelativePath());
            }
        }
    }

    private static List<PlannedFile> preflightOutputs(List<PlannedFile> files)
            throws BridgeGenerationException {
        List<PlannedFile> missing = new ArrayList<>();
        for (PlannedFile file : files) {
            if (!validateExistingOutput(file)) {
                missing.add(file);
            }
        }
        return missing;
    }

    private static void verifyCompletedOutputs(List<PlannedFile> files) throws BridgeGenerationException {
        for (PlannedFile file : files) {
            if (!validateExistingOutput(file)) {
                throw new BridgeGenerationException("Generated output disappeared before verification: "
                        + file.destination());
            }
        }
    }

    private static boolean validateExistingOutput(PlannedFile file)
            throws BridgeGenerationException {
        Path destination = file.destination();
        if (Files.isSymbolicLink(destination)) {
            throw new BridgeGenerationException("Generated output cannot be a symbolic link: " + destination);
        }
        if (!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new BridgeGenerationException("Generated output exists but is not a regular file: " + destination);
        }
        final boolean matches;
        try {
            matches = contentEquals(destination, file.bytes());
        } catch (IOException exception) {
            throw new BridgeGenerationException(
                    "Could not verify existing generated output " + destination + ": " + exception.getMessage(),
                    exception);
        }
        if (!matches) {
            throw new BridgeGenerationException(
                    "Existing generated output differs from the deterministic plan: " + destination);
        }
        return true;
    }

    private static boolean contentEquals(Path path, byte[] expected) throws IOException {
        if (Files.size(path) != expected.length) {
            return false;
        }
        try (InputStream input = Files.newInputStream(path)) {
            int offset = 0;
            byte[] buffer = new byte[8192];
            while (offset < expected.length) {
                int requested = Math.min(buffer.length, expected.length - offset);
                int read = input.read(buffer, 0, requested);
                if (read < 0) {
                    return false;
                }
                for (int index = 0; index < read; index++) {
                    if (buffer[index] != expected[offset + index]) {
                        return false;
                    }
                }
                offset += read;
            }
            return input.read() < 0;
        }
    }

    private static void publishMissing(PlannedFile file) throws IOException, BridgeGenerationException {
        Path destination = file.destination();
        Path parent = destination.getParent();
        if (parent == null) {
            throw new BridgeGenerationException("Generated output has no parent directory: " + destination);
        }
        Files.createDirectories(parent);
        rejectOutputPathComponents(file.outputRoot(), parent);

        if (validateExistingOutput(file)) {
            return;
        }

        Path temporary = Files.createTempFile(parent, ".enderfall-bridge-", ".tmp");
        try {
            Files.write(temporary, file.bytes(), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            publishWithoutReplacing(temporary, file);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void publishWithoutReplacing(Path temporary, PlannedFile file)
            throws IOException, BridgeGenerationException {
        Path destination = file.destination();
        Throwable linkFailure = null;
        try {
            Files.createLink(destination, temporary);
            return;
        } catch (FileAlreadyExistsException exception) {
            verifyConcurrentWinner(file);
            return;
        } catch (IOException | UnsupportedOperationException exception) {
            linkFailure = exception;
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(destination)) {
                verifyConcurrentWinner(file);
                return;
            }
        }

        try {
            Files.move(temporary, destination);
        } catch (FileAlreadyExistsException exception) {
            verifyConcurrentWinner(file);
        } catch (IOException exception) {
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(destination)) {
                verifyConcurrentWinner(file);
            } else {
                if (linkFailure != null) {
                    exception.addSuppressed(linkFailure);
                }
                throw exception;
            }
        }
    }

    private static void verifyConcurrentWinner(PlannedFile file) throws BridgeGenerationException {
        if (!validateExistingOutput(file)) {
            throw new BridgeGenerationException(
                    "Concurrent generator did not publish the expected output: " + file.destination());
        }
    }

    private static void rejectOutputSymlink(Path output, String label) throws BridgeGenerationException {
        if (Files.isSymbolicLink(output)) {
            throw new BridgeGenerationException(label + " cannot be a symbolic link: " + output);
        }
    }

    private static void requireInputDirectory(Path path, String label) throws BridgeGenerationException {
        if (Files.isSymbolicLink(path)) {
            throw new BridgeGenerationException(label + " cannot be a symbolic link: " + path);
        }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new BridgeGenerationException(label + " does not exist or is not a directory: " + path);
        }
    }

    private static void requireCanonicalSubdirectory(Path canonicalRoot, Path path, String label)
            throws BridgeGenerationException {
        if (!path.startsWith(canonicalRoot)) {
            throw new BridgeGenerationException(label + " escapes the canonical root: " + path);
        }
        Path current = canonicalRoot;
        for (Path component : canonicalRoot.relativize(path)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new BridgeGenerationException(label + " contains a symbolic-link component: " + current);
            }
        }
        requireInputDirectory(path, label);
    }

    private static void rejectOutputPathComponents(Path outputRoot, Path destinationParent)
            throws BridgeGenerationException {
        if (destinationParent == null) {
            return;
        }
        Path current = outputRoot;
        if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
            throw new BridgeGenerationException("Generated output root is not a directory: " + current);
        }
        for (Path component : outputRoot.relativize(destinationParent)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new BridgeGenerationException("Generated output contains a symbolic-link component: " + current);
            }
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                throw new BridgeGenerationException("Generated output parent is not a directory: " + current);
            }
        }
    }

    private static void rejectOverlap(Path first, Path second, String firstLabel, String secondLabel)
            throws BridgeGenerationException {
        if (first.startsWith(second) || second.startsWith(first)) {
            throw new BridgeGenerationException(firstLabel + " and " + secondLabel
                    + " must not be the same or nested paths: " + first + " | " + second);
        }
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static String portable(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Every Java 17 runtime must provide SHA-256", exception);
        }
    }

    private static void updateDigest(MessageDigest digest, PlannedFile file) {
        digest.update(file.kind().name().getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(file.portableRelativePath().getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(file.bytes());
        digest.update((byte) 0);
    }

    private record PlannedFile(
            OutputKind kind,
            String sourceIdentity,
            Path relativePath,
            Path outputRoot,
            Path destination,
            byte[] bytes) {
        private String portableRelativePath() {
            return portable(relativePath);
        }

        private String stableName() {
            return kind.name() + '/' + portableRelativePath();
        }
    }
}
