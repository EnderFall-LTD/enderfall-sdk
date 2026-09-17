package uk.co.enderfall.sdk.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import uk.co.enderfall.sdk.bridge.model.MappingAbi;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;

class RuntimeProjectCatalogTest {
    @Test
    void projectRoutingExactlyMatchesReviewedLoaderAndMappingAbis() throws Exception {
        Path root = sdkRoot();
        Map<String, String> actual = new TreeMap<>();
        for (String raw : Files.readAllLines(root.resolve("gradle/runtime-projects.properties"))) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] fields = line.split("=", -1);
            assertEquals(2, fields.length, line);
            assertNull(actual.put(fields[0].trim(), fields[1].trim()), "Duplicate routing: " + line);
        }
        assertEquals(TargetCatalog.standard().targetIds(), List.copyOf(actual.keySet()));
        for (var target : TargetCatalog.standard().targets()) {
            String expected = switch (target.loaderAbi()) {
                case FABRIC -> target.mappingAbi() == MappingAbi.OFFICIAL_UNOBFUSCATED
                        ? "fabric-unobfuscated" : "fabric-remapped";
                case LEGACY_FML -> "legacy-fml";
                case MODERN_NEOFORGE -> "neoforge";
            };
            assertEquals(expected, actual.get(target.id()), target.id());
            String extension = expected.startsWith("fabric-") ? ".gradle.kts" : ".gradle";
            assertTrue(Files.isRegularFile(root.resolve("gradle/targets/" + expected + extension)));
            String projectName = "runtime-generated-" + target.loader().id() + "-" + target.minecraftVersion().id();
            assertFalse(Files.exists(root.resolve(projectName + "/build.gradle")), projectName);
            assertFalse(Files.exists(root.resolve(projectName + "/build.gradle.kts")), projectName);
        }
    }

    private static Path sdkRoot() {
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path root = List.of(working, working.getParent()).stream()
                .filter(path -> Files.isRegularFile(path.resolve("gradle/runtime-projects.properties")))
                .findFirst().orElse(null);
        assertNotNull(root, "Cannot locate SDK runtime project catalog");
        return root;
    }
}
