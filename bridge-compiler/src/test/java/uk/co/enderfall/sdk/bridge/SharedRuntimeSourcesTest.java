package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.LoaderAbi;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;

class SharedRuntimeSourcesTest {
    @Test void everyDeclarationIsSharedOrExplicitlyOmittedOnEveryTarget() throws Exception {
        for (String id : TargetCatalog.standard().targetIds()) {
            var target = TargetCatalog.standard().require(id);
            boolean fabric = target.loaderAbi() == LoaderAbi.FABRIC;
            Path root = canonicalRoot().resolve(fabric ? "src/canonical/java" : "src/neoforge/java");
            Set<String> declarations = new TreeSet<>();
            try (var files = Files.walk(root)) {
                files.filter(Files::isRegularFile).forEach(path -> declarations.add(root.relativize(path).toString().replace('\\', '/')));
            }
            var emitted = SharedRuntimeSources.emit(target, declarations);
            var remaining = new TreeSet<>(declarations);
            Set<String> replaced = new HashSet<>();
            Set<String> outputs = new HashSet<>();
            for (RuntimeSource source : emitted) {
                assertTrue(replaced.add(source.canonicalRelativePath()), "Duplicate declaration " + id + ": " + source.canonicalRelativePath());
                assertTrue(outputs.add(source.relativePath()), "Duplicate output " + id + ": " + source.relativePath());
                remaining.remove(source.canonicalRelativePath());
            }
            Set<String> omitted = RuntimeSourceLayout.omittedSourcePaths(target);
            assertTrue(java.util.Collections.disjoint(replaced, omitted), "Emitted an omitted declaration: " + id);
            remaining.removeAll(omitted);
            assertEquals(Set.of(), remaining, id);
        }
    }

    private static Path canonicalRoot() {
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path candidate : List.of(working.resolve("bridge-runtime"), working.resolve("../bridge-runtime")))
            if (Files.isRegularFile(candidate.resolve("MANIFEST.sha256"))) return candidate.normalize();
        throw new IllegalStateException("Cannot locate bridge-runtime from " + working);
    }
}
