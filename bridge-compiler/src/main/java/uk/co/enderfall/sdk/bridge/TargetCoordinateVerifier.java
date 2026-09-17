package uk.co.enderfall.sdk.bridge;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import uk.co.enderfall.sdk.bridge.model.TargetCatalog;
import uk.co.enderfall.sdk.bridge.model.TargetSpec;

/** Produces and verifies stable machine-readable coordinates from the reviewed target catalog. */
final class TargetCoordinateVerifier {
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9]*");
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._+\\-]*");

    private TargetCoordinateVerifier() {
    }

    static Map<String, String> reviewedFields(String targetId) {
        TargetSpec target = TargetCatalog.standard().require(targetId);
        Map<String, String> fields = new LinkedHashMap<>();
        add(fields, "targetId", target.id());
        add(fields, "minecraftVersion", target.minecraftVersion().id());
        add(fields, "javaVersion", Integer.toString(target.javaVersion()));
        add(fields, "loader", target.loader().id());
        add(fields, "loaderVersion", target.loaderVersion());
        add(fields, "platformApiVersion", target.platformApiVersion());
        add(fields, "loaderAbi", target.loaderAbi().name());
        add(fields, "minecraftAbi", target.minecraftAbi().name());
        add(fields, "mappingAbi", target.mappingAbi().name());
        add(fields, "recipeAbi", target.recipeAbi().name());
        add(fields, "networkAbi", target.networkAbi().name());
        add(fields, "menuAbi", target.menuAbi().name());
        return Collections.unmodifiableMap(fields);
    }

    static void verifyExpectations(Map<String, String> fields, List<String> rawExpectations) {
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(rawExpectations, "rawExpectations");
        Map<String, String> expectations = new LinkedHashMap<>();
        for (String raw : rawExpectations) {
            Objects.requireNonNull(raw, "expectation");
            int separator = raw.indexOf('=');
            if (separator <= 0 || raw.indexOf('=', separator + 1) >= 0) {
                throw new IllegalArgumentException(
                        "Expectation must be exactly key=value with no additional '=': "
                                + diagnostic(raw));
            }
            String key = raw.substring(0, separator);
            String value = raw.substring(separator + 1);
            if (!SAFE_KEY.matcher(key).matches() || !SAFE_VALUE.matcher(value).matches()) {
                throw new IllegalArgumentException(
                        "Expectation contains characters outside the safe key=value format: "
                                + diagnostic(raw));
            }
            if (!fields.containsKey(key)) {
                throw new IllegalArgumentException(
                        "Unknown target coordinate key '" + key + "'. Known keys: "
                                + String.join(", ", fields.keySet()));
            }
            if (expectations.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate target coordinate expectation: " + key);
            }
        }
        for (Map.Entry<String, String> expectation : expectations.entrySet()) {
            String actual = fields.get(expectation.getKey());
            if (!actual.equals(expectation.getValue())) {
                throw new IllegalArgumentException("Target coordinate mismatch for '" + expectation.getKey()
                        + "': expected '" + expectation.getValue() + "', actual '" + actual + "'");
            }
        }
    }

    private static void add(Map<String, String> fields, String key, String value) {
        if (!SAFE_KEY.matcher(key).matches() || !SAFE_VALUE.matcher(value).matches()) {
            throw new IllegalStateException(
                    "Reviewed target coordinate cannot be represented safely as key=value: " + key);
        }
        if (fields.putIfAbsent(key, value) != null) {
            throw new IllegalStateException("Duplicate reviewed target coordinate key: " + key);
        }
    }

    private static String diagnostic(String value) {
        StringBuilder safe = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> safe.append("\\\\");
                case '\r' -> safe.append("\\r");
                case '\n' -> safe.append("\\n");
                case '\t' -> safe.append("\\t");
                default -> {
                    if (Character.isISOControl(character)) {
                        safe.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        safe.append(character);
                    }
                }
            }
        }
        return safe.toString();
    }
}
