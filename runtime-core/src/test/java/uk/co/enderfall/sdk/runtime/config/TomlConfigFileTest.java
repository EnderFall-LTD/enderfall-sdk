package uk.co.enderfall.sdk.runtime.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;
import org.tomlj.TomlVersion;
import uk.co.enderfall.sdk.api.config.ConfigHandle;
import uk.co.enderfall.sdk.api.config.ConfigKey;
import uk.co.enderfall.sdk.api.config.ConfigSpec;
import uk.co.enderfall.sdk.api.logging.ModLogger;

class TomlConfigFileTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-03T12:34:56Z"), ZoneOffset.UTC);

    @TempDir
    Path temporaryDirectory;

    @Test
    void repairsOnlyInvalidKnownValuesAndRetainsUnknownValues() throws IOException {
        ConfigSpec.Builder builder = ConfigSpec.builder();
        ConfigKey<Integer> count = builder.integer("feature.count", 5, 1, 10, "Allowed feature count.");
        ConfigSpec spec = builder.build();
        Path path = temporaryDirectory.resolve("example.toml");
        Files.writeString(path, "feature.count = 500\nfuture.setting = \"keep-me\"\n", StandardCharsets.UTF_8);

        ConfigHandle handle = TomlConfigFile.load(path, spec, new SilentLogger(), CLOCK);

        assertEquals(5, handle.get(count));
        assertTrue(Files.exists(temporaryDirectory.resolve("example.toml.invalid-20260903T123456Z.bak")));
        String repaired = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(repaired.contains("feature.count = 5"));
        assertTrue(repaired.contains("future.setting = \"keep-me\""));
    }

    @Test
    void malformedFileIsBackedUpAndRegenerated() throws IOException {
        ConfigSpec.Builder builder = ConfigSpec.builder();
        ConfigKey<Boolean> enabled = builder.booleanValue("enabled", true, "Feature toggle.");
        Path path = temporaryDirectory.resolve("broken.toml");
        Files.writeString(path, "enabled = [", StandardCharsets.UTF_8);

        ConfigHandle handle = TomlConfigFile.load(path, builder.build(), new SilentLogger(), CLOCK);

        assertTrue(handle.get(enabled));
        assertTrue(Files.exists(temporaryDirectory.resolve("broken.toml.invalid-20260903T123456Z.bak")));
        assertTrue(Files.readString(path, StandardCharsets.UTF_8).contains("enabled = true"));
    }

    @Test
    void oversizedFileIsBackedUpWithoutBeingParsed() throws IOException {
        ConfigSpec.Builder builder = ConfigSpec.builder();
        ConfigKey<Boolean> enabled = builder.booleanValue("enabled", true, "Feature toggle.");
        Path path = temporaryDirectory.resolve("oversized.toml");
        Files.writeString(path, "x".repeat((int) TomlConfigFile.MAXIMUM_FILE_BYTES + 1),
                StandardCharsets.UTF_8);

        ConfigHandle handle = TomlConfigFile.load(path, builder.build(), new SilentLogger(), CLOCK);

        assertTrue(handle.get(enabled));
        assertTrue(Files.exists(temporaryDirectory.resolve("oversized.toml.invalid-20260903T123456Z.bak")));
        assertTrue(Files.size(path) < TomlConfigFile.MAXIMUM_FILE_BYTES);
    }

    @Test
    void repairingNeverWritesAConfigThatWouldDestroyUnknownSections() throws IOException {
        // An array of tables cannot be written back as `key = value`. Rendering it that way used to
        // produce `items = [[]]` plus stray `[[]]` headers: the user's section was destroyed and the
        // file stopped parsing, so the next launch backed it up and regenerated it from defaults.
        ConfigSpec.Builder builder = ConfigSpec.builder();
        ConfigKey<Integer> count = builder.integer("feature.count", 5, 1, 10, "Allowed feature count.");
        ConfigSpec spec = builder.build();
        String original = "feature.count = 500\n\n[[items]]\nname = \"a\"\n\n[[items]]\nname = \"b\"\n";
        Path path = temporaryDirectory.resolve("sections.toml");
        Files.writeString(path, original, StandardCharsets.UTF_8);

        ConfigHandle handle = TomlConfigFile.load(path, spec, new SilentLogger(), CLOCK);

        // The known value is still repaired for this session.
        assertEquals(5, handle.get(count));
        // The original file survives untouched, so nothing outside the SDK's own keys is lost.
        assertEquals(original, Files.readString(path, StandardCharsets.UTF_8));
    }

    @Test
    void aRepairLeftOffDiskDoesNotBackUpTheFileOnEveryLaunch() throws IOException {
        // The file is never rewritten, so the invalid value is still there next launch. A backup per
        // attempt would add a `.bak` on every server start, forever, each a copy of an unchanged file.
        ConfigSpec.Builder builder = ConfigSpec.builder();
        builder.integer("feature.count", 5, 1, 10, "Allowed feature count.");
        ConfigSpec spec = builder.build();
        Path path = temporaryDirectory.resolve("sections.toml");
        Files.writeString(path, "feature.count = 500\n\n[[items]]\nname = \"a\"\n", StandardCharsets.UTF_8);

        for (int launch = 0; launch < 3; launch++) {
            TomlConfigFile.load(path, spec, new SilentLogger(), CLOCK);
        }

        try (Stream<Path> files = Files.list(temporaryDirectory)) {
            assertEquals(List.of(path), files.toList());
        }
    }

    @Test
    void everyRepairedConfigRemainsParseable() throws IOException {
        // A repair must never produce a document the next launch would reject.
        List<String> contents = Arrays.asList(
                "feature.count = 500\nfuture.setting = \"keep-me\"\n",
                "feature.count = 500\n[[items]]\nname = \"a\"\n",
                "feature.count = 500\nitems = [{name=\"a\"}, {name=\"b\"}]\n",
                "feature.count = 500\n[section]\nkey = 1\n",
                "feature.count = 500\nvalues = [\n  1,\n  2,\n]\n",
                "feature.count = 500\n[a.b]\nc = 2\n");
        for (String original : contents) {
            ConfigSpec.Builder builder = ConfigSpec.builder();
            ConfigKey<Integer> count = builder.integer("feature.count", 5, 1, 10, "Count.");
            Path path = temporaryDirectory.resolve("case.toml");
            Files.writeString(path, original, StandardCharsets.UTF_8);

            TomlConfigFile.load(path, builder.build(), new SilentLogger(), CLOCK);

            String written = Files.readString(path, StandardCharsets.UTF_8);
            TomlParseResult reparsed = Toml.parse(written, TomlVersion.V1_0_0);
            assertTrue(reparsed.errors().isEmpty(),
                    "Rewritten config does not parse: " + reparsed.errors() + "\n" + written);
            // Either the repair was persisted or the file was left exactly as the user wrote it.
            assertTrue(written.equals(original) || Long.valueOf(5).equals(reparsed.getLong("feature.count")),
                    "Config was rewritten without persisting the repair:\n" + written);
            // Parsing is not enough: every value the user wrote outside the SDK's keys must survive.
            assertEquals(valuesOutside("feature.count", Toml.parse(original, TomlVersion.V1_0_0)),
                    valuesOutside("feature.count", reparsed), "Rewrite lost or moved user values:\n" + written);
        }
    }

    @Test
    void aSingleEntryArrayOfTablesIsNotFlattenedIntoTheDocumentRoot() throws IOException {
        // One `[[items]]` entry renders as `items = [[]]` followed by its keys, which parses: the
        // entry's keys silently move to the root and `items` becomes an array holding an empty array.
        ConfigSpec.Builder builder = ConfigSpec.builder();
        ConfigKey<Integer> count = builder.integer("feature.count", 5, 1, 10, "Allowed feature count.");
        String original = "feature.count = 500\n\n[[items]]\nname = \"a\"\n";
        Path path = temporaryDirectory.resolve("single.toml");
        Files.writeString(path, original, StandardCharsets.UTF_8);

        ConfigHandle handle = TomlConfigFile.load(path, builder.build(), new SilentLogger(), CLOCK);

        assertEquals(5, handle.get(count));
        assertEquals(original, Files.readString(path, StandardCharsets.UTF_8));
    }

    private static Map<String, Object> valuesOutside(String sdkKey, TomlTable document) {
        Map<String, Object> values = new TreeMap<>();
        document.dottedEntrySet().forEach(entry -> values.put(entry.getKey(), plain(entry.getValue())));
        values.remove(sdkKey);
        return values;
    }

    private static Object plain(Object value) {
        if (value instanceof TomlTable table) {
            Map<String, Object> map = new TreeMap<>();
            table.entrySet().forEach(entry -> map.put(entry.getKey(), plain(entry.getValue())));
            return map;
        }
        if (value instanceof TomlArray array) {
            List<Object> list = new ArrayList<>(array.size());
            for (int index = 0; index < array.size(); index++) {
                list.add(plain(array.get(index)));
            }
            return list;
        }
        return value;
    }

    @Test
    void aSafeRepairStillPersistsRetainedUnknownValues() throws IOException {
        ConfigSpec.Builder builder = ConfigSpec.builder();
        ConfigKey<Integer> count = builder.integer("feature.count", 5, 1, 10, "Allowed feature count.");
        Path path = temporaryDirectory.resolve("safe.toml");
        Files.writeString(path, "feature.count = 500\nfuture.setting = \"keep-me\"\n", StandardCharsets.UTF_8);

        TomlConfigFile.load(path, builder.build(), new SilentLogger(), CLOCK);

        String written = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(written.contains("feature.count = 5"));
        assertTrue(written.contains("future.setting = \"keep-me\""));
    }

    @Test
    void malformedSensitiveValuesAreNeverIncludedInWarnings() throws IOException {
        ConfigSpec.Builder builder = ConfigSpec.builder();
        builder.value("token", uk.co.enderfall.sdk.api.config.ConfigType.STRING, "",
                "Private token.", uk.co.enderfall.sdk.api.config.ConfigValidator.acceptingAll(), true);
        Path path = temporaryDirectory.resolve("sensitive.toml");
        String secret = "do-not-log-this-secret";
        Files.writeString(path, "token = \"" + secret, StandardCharsets.UTF_8);
        CapturingLogger logger = new CapturingLogger();

        TomlConfigFile.load(path, builder.build(), logger, CLOCK);

        assertTrue(logger.messages.stream().noneMatch(message -> message.contains(secret)));
    }

    private static final class SilentLogger implements ModLogger {
        @Override public void debug(String message, Object... arguments) { }
        @Override public void info(String message, Object... arguments) { }
        @Override public void warn(String message, Object... arguments) { }
        @Override public void error(String message, Object... arguments) { }
        @Override public void error(String message, Throwable error, Object... arguments) { }
    }

    private static final class CapturingLogger implements ModLogger {
        private final List<String> messages = new ArrayList<>();

        @Override public void debug(String message, Object... arguments) { record(message, arguments); }
        @Override public void info(String message, Object... arguments) { record(message, arguments); }
        @Override public void warn(String message, Object... arguments) { record(message, arguments); }
        @Override public void error(String message, Object... arguments) { record(message, arguments); }
        @Override public void error(String message, Throwable error, Object... arguments) {
            record(message, arguments);
        }

        private void record(String message, Object... arguments) {
            messages.add(message + Arrays.toString(arguments));
        }
    }
}
