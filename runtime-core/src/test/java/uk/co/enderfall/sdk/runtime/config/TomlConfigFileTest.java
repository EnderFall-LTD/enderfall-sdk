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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

    private static final class SilentLogger implements ModLogger {
        @Override public void debug(String message, Object... arguments) { }
        @Override public void info(String message, Object... arguments) { }
        @Override public void warn(String message, Object... arguments) { }
        @Override public void error(String message, Object... arguments) { }
        @Override public void error(String message, Throwable error, Object... arguments) { }
    }
}
