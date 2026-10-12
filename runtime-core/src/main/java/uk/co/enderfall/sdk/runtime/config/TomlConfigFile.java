package uk.co.enderfall.sdk.runtime.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;
import org.tomlj.TomlVersion;
import uk.co.enderfall.sdk.api.config.ConfigKey;
import uk.co.enderfall.sdk.api.config.ConfigSpec;
import uk.co.enderfall.sdk.api.config.ConfigType;
import uk.co.enderfall.sdk.api.config.ValidationResult;
import uk.co.enderfall.sdk.api.logging.ModLogger;

final class TomlConfigFile {
    static final long MAXIMUM_FILE_BYTES = 1_048_576;
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'")
            .withLocale(Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private TomlConfigFile() {
    }

    static DefaultConfigHandle load(Path path, ConfigSpec spec, ModLogger logger) throws IOException {
        return load(path, spec, logger, Clock.systemUTC());
    }

    static DefaultConfigHandle load(Path path, ConfigSpec spec, ModLogger logger, Clock clock) throws IOException {
        Files.createDirectories(path.getParent());
        if (!Files.exists(path)) {
            Map<ConfigKey<?>, Object> defaults = defaults(spec);
            writeAtomically(path, render(spec, defaults, Map.of()));
            return new DefaultConfigHandle(path, defaults);
        }

        if (Files.size(path) > MAXIMUM_FILE_BYTES) {
            backup(path, clock);
            logger.warn("Config {} exceeds the {} byte safety limit; backed up and regenerated",
                    path, MAXIMUM_FILE_BYTES);
            Map<ConfigKey<?>, Object> defaults = defaults(spec);
            writeAtomically(path, render(spec, defaults, Map.of()));
            return new DefaultConfigHandle(path, defaults);
        }

        TomlParseResult parsed = Toml.parse(path, TomlVersion.V1_0_0);
        if (parsed.hasErrors()) {
            backup(path, clock);
            logger.warn("Malformed config {}; backed up and regenerated", path);
            Map<ConfigKey<?>, Object> defaults = defaults(spec);
            writeAtomically(path, render(spec, defaults, Map.of()));
            return new DefaultConfigHandle(path, defaults);
        }

        Map<String, Object> unknown = new LinkedHashMap<>();
        parsed.dottedEntrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> unknown.put(entry.getKey(), entry.getValue()));

        Map<ConfigKey<?>, Object> values = new LinkedHashMap<>();
        boolean repaired = false;
        for (ConfigKey<?> key : spec.keys()) {
            unknown.remove(key.path());
            Object raw = parsed.get(key.path());
            Object converted = convert(raw, key.type());
            if (converted == null || !validate(key, converted).valid()) {
                repaired = true;
                values.put(key, key.defaultValue());
                logger.warn("Repairing invalid config key {} in {}", key.path(), path);
            } else {
                values.put(key, immutableValue(converted));
            }
        }

        if (repaired) {
            // Only rewrite when the document we would produce reads back as exactly the values we
            // hold. Unknown sections are copied straight from the parsed file, and an array of tables
            // cannot be written back as `key = value`: two entries stop parsing, and one entry still
            // parses but moves its keys to the document root. Either way a user's unrelated settings
            // would be lost. The file is then left untouched, so there is nothing to back up; backing
            // it up anyway would add a copy on every launch, since the invalid value is never removed.
            String rendered = render(spec, values, unknown);
            if (readsBackAs(rendered, values, unknown)) {
                backup(path, clock);
                writeAtomically(path, rendered);
            } else {
                logger.warn("Config {} was repaired in memory but left unchanged on disk because its "
                        + "unknown sections cannot be written back safely", path);
            }
        }
        return new DefaultConfigHandle(path, values);
    }

    private static boolean readsBackAs(String rendered, Map<ConfigKey<?>, Object> values,
                                       Map<String, Object> unknown) {
        TomlParseResult reparsed = Toml.parse(rendered, TomlVersion.V1_0_0);
        if (!reparsed.errors().isEmpty()) {
            return false;
        }
        Map<String, Object> readBack = new HashMap<>();
        reparsed.dottedEntrySet().forEach(entry -> readBack.put(entry.getKey(), plain(entry.getValue())));
        // Known values were validated and are rendered by the SDK itself, so only their presence is
        // checked. Every other key must read back with the value the user wrote and nothing extra.
        for (ConfigKey<?> key : values.keySet()) {
            if (readBack.remove(key.path()) == null) {
                return false;
            }
        }
        Map<String, Object> retained = new HashMap<>();
        unknown.forEach((key, value) -> retained.put(key, plain(value)));
        return readBack.equals(retained);
    }

    private static Object plain(Object value) {
        if (value instanceof TomlTable table) {
            Map<String, Object> map = new HashMap<>();
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

    static Map<ConfigKey<?>, Object> defaults(ConfigSpec spec) {
        Map<ConfigKey<?>, Object> values = new LinkedHashMap<>();
        spec.keys().forEach(key -> values.put(key, key.defaultValue()));
        return values;
    }

    @SuppressWarnings("unchecked")
    private static <T> ValidationResult validate(ConfigKey<T> key, Object value) {
        return key.validator().validate((T) value);
    }

    private static Object convert(Object value, ConfigType<?> type) {
        if (value == null) {
            return null;
        }
        return switch (type.kind()) {
            case BOOLEAN -> value instanceof Boolean ? value : null;
            case INTEGER -> value instanceof Long number && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE
                    ? number.intValue() : null;
            case LONG -> value instanceof Long ? value : null;
            case DOUBLE -> value instanceof Double number && Double.isFinite(number) ? value : null;
            case STRING -> value instanceof String ? value : null;
            case ENUM -> convertEnum(value, type.valueClass());
            case LIST -> convertList(value, type.elementType());
        };
    }

    private static Object convertEnum(Object value, Class<?> enumClass) {
        if (!(value instanceof String text)) {
            return null;
        }
        for (Object constant : enumClass.getEnumConstants()) {
            Enum<?> enumValue = (Enum<?>) constant;
            if (enumValue.name().equalsIgnoreCase(text)) {
                return enumValue;
            }
        }
        return null;
    }

    private static Object convertList(Object value, ConfigType<?> elementType) {
        if (!(value instanceof TomlArray array)) {
            return null;
        }
        List<Object> converted = new ArrayList<>(array.size());
        for (int index = 0; index < array.size(); index++) {
            Object element = convert(array.get(index), elementType);
            if (element == null) {
                return null;
            }
            converted.add(element);
        }
        return List.copyOf(converted);
    }

    private static Object immutableValue(Object value) {
        return value instanceof List<?> list ? List.copyOf(list) : value;
    }

    private static Path backup(Path path, Clock clock) throws IOException {
        String timestamp = BACKUP_TIMESTAMP.format(Instant.now(clock).truncatedTo(ChronoUnit.SECONDS));
        Path backup = path.resolveSibling(path.getFileName() + ".invalid-" + timestamp + ".bak");
        int collision = 0;
        while (Files.exists(backup)) {
            collision++;
            backup = path.resolveSibling(path.getFileName() + ".invalid-" + timestamp + '-' + collision + ".bak");
        }
        Files.copy(path, backup);
        return backup;
    }

    private static void writeAtomically(Path path, String content) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                moved = true;
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("Atomic config replacement is unsupported for " + path, exception);
            }
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static String render(ConfigSpec spec, Map<ConfigKey<?>, Object> values, Map<String, Object> unknown) {
        StringBuilder output = new StringBuilder("# Generated and validated by EnderFall SDK.\n\n");
        for (ConfigKey<?> key : spec.keys()) {
            if (!key.comment().isBlank()) {
                for (String line : key.comment().split("\\R")) {
                    output.append("# ").append(line).append('\n');
                }
            }
            output.append(key.path()).append(" = ").append(renderValue(values.get(key))).append("\n\n");
        }
        if (!unknown.isEmpty()) {
            output.append("# Unknown keys retained from the previous file.\n");
            unknown.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> output
                    .append(entry.getKey()).append(" = ").append(renderValue(entry.getValue())).append('\n'));
        }
        return output.toString();
    }

    private static String renderValue(Object value) {
        Objects.requireNonNull(value, "value");
        if (value instanceof String text) {
            return '"' + Toml.tomlEscape(text).toString() + '"';
        }
        if (value instanceof Enum<?> enumValue) {
            return '"' + enumValue.name().toLowerCase(Locale.ROOT) + '"';
        }
        if (value instanceof TomlArray array) {
            return array.toToml();
        }
        if (value instanceof TomlTable table) {
            return table.toToml();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(TomlConfigFile::renderValue)
                    .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
        }
        return value.toString();
    }
}
